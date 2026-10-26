package gmbh.vanella.lector.core

import kotlin.io.path.createParentDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * M11 sections 5 and 6: an edit that can't be applied is rejected with what the model needs to correct it, and every
 * failed attempt on a cluster goes back to the model. The edits are the models' own (evidence.md).
 */
class FixLoopFeedbackTest : FixLoopFixture() {
    private val pdfPages =
        """
        package com.example;

        import java.io.File;
        import java.io.IOException;
        import org.apache.pdfbox.pdmodel.PDDocument;

        public class PdfPages {
            public static int count(File file) throws IOException {
                try (PDDocument document = PDDocument.load(file)) {
                    return document.getNumberOfPages();
                }
            }
        }
        """.trimIndent() + "\n"

    // pdfbox-3, qwen3-coder:30b: the edit lost the `import ` of its third line (evidence.md).
    private val brokenImports =
        Edit(
            "src/main/java/com/example/PdfPages.java",
            "import java.io.File;\nimport java.io.IOException;\norg.apache.pdfbox.pdmodel.PDDocument;",
            "import java.io.File;\nimport java.io.IOException;\nimport org.apache.pdfbox.pdmodel.PDDocument;\nimport org.apache.pdfbox.pdfparser.PDFParser;",
        )

    @Test
    fun `a search text that isn't in the file names the edit and shows the most similar lines with their numbers`() {
        commit("src/main/java/com/example/PdfPages.java" to pdfPages)
        val load =
            Edit(
                "src/main/java/com/example/PdfPages.java",
                "        try (PDDocument document = PDDocument.load(file)) {",
                "        try (PDDocument document = PDFParser.load(file)) {",
            )

        val reason = rejectionOf(brokenImports, load)

        assertContains(reason, Regex("""(?i)\bedit 1\b"""), "names the edit")
        assertContains(reason, "src/main/java/com/example/PdfPages.java")
        assertContains(reason, Regex("""\b3\b\D{0,6}import java\.io\.File;"""), "line 3 with its number")
        assertContains(reason, Regex("""\b4\b\D{0,6}import java\.io\.IOException;"""), "line 4 with its number")
        assertContains(reason, Regex("""\b5\b\D{0,6}import org\.apache\.pdfbox\.pdmodel\.PDDocument;"""), "line 5 with its number")
        assertFalse("public class PdfPages" in reason, "only the most similar window, not the file")
        assertFalse("package com.example;" in reason, "only the most similar window, not the file")
        assertEquals(pdfPages, repo.resolve("src/main/java/com/example/PdfPages.java").readText(), "no near match is applied")
    }

    @Test
    fun `a failing second edit is named, with the line numbers of the committed file, not of the half-edited one`() {
        commit("src/main/java/com/example/PdfPages.java" to pdfPages)
        val addImport =
            Edit("src/main/java/com/example/PdfPages.java", "import java.io.File;", "import java.io.File;\nimport java.util.List;")

        val reason = rejectionOf(addImport, brokenImports)

        assertContains(reason, Regex("""(?i)\bedit 2\b"""))
        assertContains(reason, Regex("""\b5\b\D{0,6}import org\.apache\.pdfbox\.pdmodel\.PDDocument;"""), "line 5 as committed, not 6")
    }

    @Test
    fun `a search text found twice names the lines of both matches`() {
        val timerTest =
            """
            class TimerTest {
                @Test
                void updates() {
                    timer.update(1, SECONDS);
                    verifyZeroInteractions(reservoir);
                }
                @Test
                void ignoresNegative() {
                    verifyZeroInteractions(reservoir);
                }
            }
            """.trimIndent() + "\n"
        commit("TimerTest.java" to timerTest)
        // dropwizard-metrics-mockito-4, qwen2.5-coder:14b.
        val rename = Edit("TimerTest.java", "verifyZeroInteractions(reservoir);", "verifyNoInteractions(reservoir);")

        val reason = rejectionOf(rename)

        assertContains(reason, Regex("""(?i)\bedit 1\b"""))
        assertContains(reason, "5, 9")
    }

    @Test
    fun `a search text found many times names the first five lines and counts the rest`() {
        commit("Calls.java" to (1..8).joinToString("") { "call();\n" })

        val reason = rejectionOf(Edit("Calls.java", "call();", "done();"))

        assertContains(reason, "1, 2, 3, 4, 5")
        assertFalse(Regex("""\b6, 7""").containsMatchIn(reason), "lines after the fifth aren't listed")
        assertContains(reason, "and 3 more")
    }

    @Test
    fun `an edit of a file that doesn't exist names the tracked file of that name, and is not a guardrail rejection`() {
        commit("src/test/java/io/dropwizard/kafka/integration/DropwizardKafkaIT.java" to "import kafka.server.BrokerState;\n")
        // dropwizard-kafka-clients-3, qwen3-coder:30b: the path without src/test/java.
        val guessed =
            Edit(
                "io/dropwizard/kafka/integration/DropwizardKafkaIT.java",
                "import kafka.server.BrokerState;",
                "import org.apache.kafka.metadata.BrokerState;",
            )
        val adapter = FakeAdapter()

        val result = loop(adapter, ScriptedLlm(edits(guessed)), Budgets(attempts = 1)).run(repo, context, adapter.initial())

        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts.single().outcome)
        assertContains(rejected.reason, "no such file")
        assertContains(rejected.reason, "src/test/java/io/dropwizard/kafka/integration/DropwizardKafkaIT.java")
        assertEquals(emptyList(), rejected.violations)
        assertEquals(emptyMap(), result.rejectionsPerRule())
    }

    @Test
    fun `every request carries every earlier failed attempt on its cluster, of every kind, with its reason`() {
        val notApplied = Edit("App.java", "brokn();", "fixed();")
        val stub = Edit("App.java", "broken();", "return null;")
        val noProgress = Edit("App.java", "broken();", "broken(); // still broken")
        val adapter = FakeAdapter()
        val llm =
            ScriptedLlm(
                edits(notApplied),
                edits(stub),
                edits(noProgress),
                { Proposal.Invalid("the answer is not JSON") },
                edits(noProgress),
                edits(noProgress),
                edits(fix),
            )
        val budgets = Budgets(attemptsPerCluster = 6, attempts = 6, attemptsWithoutProgress = 6)

        val result = loop(adapter, llm, budgets).run(repo, context, adapter.initial())

        assertIs<AttemptOutcome.Accepted>(result.attempts.last().outcome)
        val earlier = llm.requests.last().previousAttempts
        assertEquals(
            listOf(listOf(notApplied), listOf(stub), listOf(noProgress), emptyList(), listOf(noProgress)),
            earlier.map { it.edits },
        )
        assertContains(earlier[0].failure, "not in the file")
        assertContains(earlier[0].failure, Regex("""(?i)\bedit 1\b"""), "with the detail of the failed edit")
        assertContains(earlier[1].failure, "[stubbing]")
        assertTrue(earlier[2].failure.startsWith("no progress"), earlier[2].failure)
        assertContains(earlier[2].failure, "cannot find symbol broken", message = "with the errors after the edits")
        assertContains(earlier[3].failure, "invalid")
        assertContains(earlier[4].failure, "repeated attempt 3")
    }

    private fun commit(vararg files: Pair<String, String>) {
        files.forEach { (name, content) -> repo.resolve(name).createParentDirectories().writeText(content) }
        git("add", ".")
        git("commit", "--quiet", "-m", "more code")
    }

    /** The reason the only attempt was rejected, when [edits] are the model's answer. */
    private fun rejectionOf(vararg edits: Edit): String {
        val adapter = FakeAdapter()
        val result = loop(adapter, ScriptedLlm(edits(*edits)), Budgets(attempts = 1)).run(repo, context, adapter.initial())
        return assertIs<AttemptOutcome.Rejected>(result.attempts.single().outcome).reason
    }
}
