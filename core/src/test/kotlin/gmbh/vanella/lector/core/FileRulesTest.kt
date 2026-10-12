package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class FileRulesTest {
    private val repo = Path.of("/work/repo")
    private val scope =
        Scope(repo, setOf("App.java", "src/generated/Client.java", "pom.xml")) {
            !it.startsWith("target/") &&
                "generated" !in it
        }

    @Test
    fun `scope lets a tracked, editable file inside the repository pass, however its path is written`() {
        assertEquals(emptyList(), scope.check(listOf("App.java", "./App.java", "src/../App.java", "/work/repo/pom.xml")))
    }

    @Test
    fun `scope rejects build output, generated code, ignored or untracked files, and paths outside the repository`() {
        assertEquals(
            listOf(
                Violation("scope", "target/classes/App.java", null, "the file is build output or generated code, which is never edited"),
                Violation("scope", "src/generated/Client.java", null, "the file is build output or generated code, which is never edited"),
                Violation("scope", "local.properties", null, "Git doesn't track the file, so it is generated or ignored"),
                Violation("scope", ".git/config", null, "Git doesn't track the file, so it is generated or ignored"),
                Violation("scope", "../other/App.java", null, "the file is outside the repository"),
                Violation("scope", "/etc/hosts", null, "the file is outside the repository"),
            ),
            scope.check(
                listOf(
                    "target/classes/App.java",
                    "src/generated/Client.java",
                    "local.properties",
                    ".git/config",
                    "../other/App.java",
                    "/etc/hosts",
                ),
            ),
        )
    }

    @Test
    fun `scope reports a file once, however many edits it has`() {
        assertEquals(1, scope.check(listOf("local.properties", "local.properties")).size)
    }

    @Test
    fun `the guardrails check the scope against the files Git tracks`(
        @TempDir repo: Path,
    ) {
        val git = { args: List<String> -> assertEquals(0, runProcess(listOf("git") + args, repo, 30.seconds).exitCode) }
        git(listOf("init", "--quiet"))
        repo.resolve("App.java").writeText("class App {}\n")
        repo.resolve("Local.java").writeText("class Local {}\n")
        git(listOf("add", "App.java"))

        val violations = Guardrails(EditableEverywhere, DEFAULT_MAX_CHANGED_LINES).checkScope(repo, listOf("App.java", "Local.java"))

        assertEquals(listOf("Local.java"), violations.map { it.file })
    }

    @Test
    fun `diff size lets 50 changed lines pass and rejects 51, added and removed counted over all files`() {
        val fifty = listOf(change("a.txt", lines(20), lines(20, "new")), change("b.txt", lines(5), lines(5, "new")))
        val fiftyOne = fifty + change("c.txt", "x\n", "x\ny\n")

        assertEquals(emptyList(), DiffSize(50).check(AttemptChanges(fifty, emptyList())))
        assertEquals(
            listOf(Violation("diff-size", null, null, "the edits change 51 lines (added plus removed), the limit is 50")),
            DiffSize(50).check(AttemptChanges(fiftyOne, emptyList())),
        )
        assertEquals(1, DiffSize(10).check(AttemptChanges(fifty.take(1), emptyList())).size, "the limit is configurable")
    }

    @Test
    fun `the build-file blocklist rejects added lines the adapter names, only in build files`() {
        val blocklist =
            BuildFileBlocklist({ it == "pom.xml" }) { _, content, line ->
                if ("skip" in
                    content.lines()[line - 1]
                ) {
                    "skips tests"
                } else {
                    null
                }
            }
        val pom = change("pom.xml", "<a/>\n<skip>false</skip>\n", "<a/>\n<skip>false</skip>\n<skip>true</skip>\n<b/>\n")
        val source = change("App.java", "", "// skip\n")

        assertEquals(
            listOf(Violation("build-file", "pom.xml", 3, "skips tests")),
            blocklist.check(AttemptChanges(listOf(pom, source), emptyList())),
        )
    }

    @Test
    fun `a violation reads as rule, location, and reason`() {
        assertEquals("[stubbing] App.java:3: a new stub", Violation("stubbing", "App.java", 3, "a new stub").toString())
        assertEquals("[scope] pom.xml: outside", Violation("scope", "pom.xml", null, "outside").toString())
        assertEquals("[diff-size] too big", Violation("diff-size", null, null, "too big").toString())
    }

    private fun lines(
        count: Int,
        prefix: String = "old",
    ) = (1..count).joinToString("") { "$prefix $it\n" }

    private fun change(
        file: String,
        before: String,
        after: String,
    ) = FileChange(file, before, after) { _, _ -> null }
}
