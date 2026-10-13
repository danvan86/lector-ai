package gmbh.vanella.lector.llm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Edit
import gmbh.vanella.lector.core.FixRequest
import gmbh.vanella.lector.core.PreviousAttempt
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.Snippet
import gmbh.vanella.lector.core.UpdateType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptTest {
    private val change = DependencyChange("maven", "com.google.guava:guava", "18.0", "21.0", UpdateType.MAJOR)
    private val error =
        Diagnostic(
            "src/main/java/App.java",
            3,
            9,
            null,
            "cannot find symbol\nsymbol:   method firstNonNull(String,String)",
            Severity.ERROR,
            "javac",
        )
    private val snippet =
        Snippet("src/main/java/App.java", 1, 5, "class App {\n    String name() {\n        return Objects.firstNonNull(a, b);\n    }\n}\n")
    private val request =
        FixRequest(
            changes = listOf(change),
            diagnostics = listOf(error),
            snippets = listOf(snippet),
            grounding = "public final class com.google.common.base.MoreObjects {\n}",
            releaseNotes = "Objects.firstNonNull was removed; use MoreObjects.firstNonNull.",
            recipeDiff = "--- a/pom.xml\n+++ b/pom.xml",
            previousAttempts =
                listOf(
                    PreviousAttempt(
                        listOf(Edit("src/main/java/App.java", "Objects.firstNonNul(", "x")),
                        "src/main/java/App.java: the search text is not in the file",
                    ),
                ),
            temperature = 0.3,
        )

    @Test
    fun `a request for several changes names each of them`() {
        val boot = DependencyChange("maven", "org.springframework.boot:spring-boot", "3.3.5", "3.4.1", UpdateType.MINOR)

        val prompt = Prompt.user(request.copy(changes = listOf(change, boot)))

        assertContains(
            prompt,
            "The update of com.google.guava:guava from 18.0 to 21.0 and org.springframework.boot:spring-boot from 3.3.5 to 3.4.1 " +
                "broke this code.",
        )
    }

    @Test
    fun `a request for many changes names the first ten and counts the rest, so the API still fits`() {
        val many = (1..1000).map { DependencyChange("maven", "org.example:lib$it", "1.0", "1.1", UpdateType.MINOR) }

        val prompt = Prompt.user(request.copy(changes = many))

        assertContains(prompt, "The update of org.example:lib1 from 1.0 to 1.1, org.example:lib2 from 1.0 to 1.1, ")
        assertContains(prompt, ", org.example:lib10 from 1.0 to 1.1 and 990 more broke this code.")
        assertFalse("org.example:lib11 from" in prompt)
        assertContains(prompt, "public final class com.google.common.base.MoreObjects {\n}")
    }

    @Test
    fun `the system prompt asks for search and replace edits in JSON, never whole files`() {
        assertContains(Prompt.SYSTEM, "\"edits\"")
        assertContains(Prompt.SYSTEM, "exactly once")
        assertContains(Prompt.SYSTEM, "never return whole files")
    }

    @Test
    fun `the request shows the change, the errors, the code, the API, the release notes, the recipe diff, and earlier attempts`() {
        val prompt = Prompt.user(request)

        assertContains(prompt, "The update of com.google.guava:guava from 18.0 to 21.0 broke this code.")
        assertContains(prompt, "- [javac] src/main/java/App.java:3:9: cannot find symbol\n  symbol:   method firstNonNull(String,String)")
        assertContains(prompt, "### src/main/java/App.java, lines 1-5\n```\n${snippet.text}```")
        assertContains(prompt, "## API of the new version\n\npublic final class com.google.common.base.MoreObjects {\n}")
        assertContains(prompt, "## Release notes\n\nObjects.firstNonNull was removed; use MoreObjects.firstNonNull.")
        assertContains(prompt, "## Changes the recipe already made\n\n```diff\n--- a/pom.xml\n+++ b/pom.xml\n```")
        assertContains(prompt, "### Attempt 1, rejected: src/main/java/App.java: the search text is not in the file")
        assertContains(prompt, "\"search\":\"Objects.firstNonNul(\"")
        val order = listOf("## Errors", "## Code", "## API", "## Release notes", "## Changes the recipe", "## Earlier attempts")
        assertEquals(order, order.sortedBy { prompt.indexOf(it) })
    }

    @Test
    fun `missing parts are left out, and a test failure shows its test`() {
        val failure = Diagnostic(null, null, null, "AssertionError", "expected 1", Severity.ERROR, "surefire", "AppTest#runs")
        val bare =
            request.copy(
                diagnostics = listOf(failure),
                snippets = emptyList(),
                grounding = "",
                releaseNotes = null,
                recipeDiff = null,
                previousAttempts = emptyList(),
            )

        val prompt = Prompt.user(bare)

        assertContains(prompt, "- [surefire] AppTest#runs: expected 1")
        listOf("## Code", "## API", "## Release notes", "## Changes the recipe", "## Earlier attempts").forEach {
            assertFalse(it in prompt, "$it is left out")
        }
    }

    @Test
    fun `a build error without a location shows only its message`() {
        val buildError = Diagnostic(null, null, null, null, "Could not find artifact", Severity.ERROR, "maven")

        assertContains(Prompt.user(request.copy(diagnostics = listOf(buildError))), "- [maven] Could not find artifact\n")
    }

    @Test
    fun `an oversized request trims grounding, release notes, and recipe diff, never the errors or the code`() {
        val bigSnippet = snippet.copy(lastLine = 81, text = "    code line\n".repeat(81))
        val oversized =
            request.copy(
                snippets = listOf(bigSnippet),
                grounding = "g".repeat(100_000),
                releaseNotes = "r".repeat(100_000),
                recipeDiff = "d".repeat(100_000),
            )

        val prompt = Prompt.user(oversized)

        assertTrue(Prompt.SYSTEM.length + prompt.length <= Prompt.MAX_CHARS, "fits: ${prompt.length}")
        assertContains(prompt, bigSnippet.text)
        assertContains(prompt, error.message.lines().last())
        assertContains(prompt, "[trimmed to fit the context]")
        assertTrue(prompt.count { it == 'g' } > prompt.count { it == 'r' }, "the API is trimmed last")
    }

    @Test
    fun `errors and code that alone exceed the context are still sent in full, without the optional parts`() {
        val huge = request.copy(snippets = List(10) { snippet.copy(firstLine = it * 100, text = "x".repeat(5_000) + "\n") })

        val prompt = Prompt.user(huge)

        assertEquals(10, Regex("x{5000}").findAll(prompt).count())
        assertFalse("MoreObjects" in prompt)
        assertFalse("## Release notes" in prompt)
        assertFalse(Prompt.fits(prompt), "the client warns about it")
        assertTrue(Prompt.fits(Prompt.user(request)))
    }
}
