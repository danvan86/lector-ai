package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class EditTest {
    @TempDir
    lateinit var repo: Path

    private val app = "src/App.java"
    private val original =
        """
        class App {
            void run() {
                String name = "lector";
                greet(name);
            }
        }
        """.trimIndent() + "\n"

    @BeforeTest
    fun `create a source file`() {
        repo
            .resolve("src")
            .createDirectories()
            .resolve("App.java")
            .writeText(original)
    }

    @Test
    fun `an exact, unique match is replaced`() {
        val result = applyEdits(repo, listOf(Edit(app, "greet(name);", "welcome(name);")))

        assertIs<EditResult.Applied>(result)
        assertEquals(original.replace("greet(name);", "welcome(name);"), text())
    }

    @Test
    fun `a match that differs only in whitespace is replaced through the tolerant match`() {
        val search = "\n  void run() {\n      String  name = \"lector\";\t\n"
        val replace = "    void run() {\n        String name = \"Lector\";"

        val result = applyEdits(repo, listOf(Edit(app, search, replace)))

        assertIs<EditResult.Applied>(result)
        assertEquals(original.replace("\"lector\"", "\"Lector\""), text())
    }

    @Test
    fun `several edits apply in order, each to the result of the one before`() {
        val edits = listOf(Edit(app, "greet(name);", "welcome(name);"), Edit(app, "welcome(name);", "welcome(name, 1);"))

        val result = applyEdits(repo, edits)

        assertIs<EditResult.Applied>(result)
        assertEquals(original.replace("greet(name);", "welcome(name, 1);"), text())
    }

    @Test
    fun `an ambiguous exact match rejects the whole attempt, and no edit is written`() {
        val edits = listOf(Edit(app, "class App", "class Main"), Edit(app, "name", "user"))

        val result = applyEdits(repo, edits)

        assertEquals(EditResult.Failed("edit 2 ($app): the search text matches 2 times, at lines 3, 4; it must match exactly once"), result)
        assertEquals(original, text())
    }

    @Test
    fun `an ambiguous tolerant match rejects the attempt`() {
        repo.resolve(app).writeText("a();\n  b();\na();\n b();\n")

        val result = applyEdits(repo, listOf(Edit(app, "a();\nb();", "c();")))

        assertEquals(EditResult.Failed("edit 1 ($app): the search text matches 2 times, at lines 1, 3; it must match exactly once"), result)
    }

    @Test
    fun `a search text that isn't there rejects the attempt`() {
        val result = applyEdits(repo, listOf(Edit(app, "farewell(name);", "bye(name);")))

        assertEquals(EditResult.Failed("edit 1 ($app): the search text is not in the file"), result)
        assertEquals(original, text())
    }

    @Test
    fun `an empty search text rejects the attempt`() {
        val result = applyEdits(repo, listOf(Edit(app, " \n", "class Other {}")))

        assertEquals(EditResult.Failed("edit 1 ($app): the search text is empty"), result)
    }

    @Test
    fun `edits that change nothing reject the attempt`() {
        val same = applyEdits(repo, listOf(Edit(app, "greet(name);", "greet(name);")))
        val undone = applyEdits(repo, listOf(Edit(app, "greet(name);", "welcome(name);"), Edit(app, "welcome(name);", "greet(name);")))

        assertEquals(EditResult.Failed("the edits change nothing"), same)
        assertEquals(EditResult.Failed("the edits change nothing"), undone)
        assertEquals(original, text())
    }

    @Test
    fun `a file that isn't in the repository rejects the attempt`() {
        val outside = repo.parent.resolve("Outside.java").toString()
        listOf("src/Missing.java", "src", "../Outside.java", outside, ".git/config").forEach { file ->
            if (file == ".git/config") {
                repo
                    .resolve(".git")
                    .createDirectories()
                    .resolve("config")
                    .writeText("[core]\n")
            }

            val result = applyEdits(repo, listOf(Edit(app, "greet", "welcome"), Edit(file, "core", "x")))

            assertEquals(EditResult.Failed("edit 2 ($file): no such file in the repository"), result)
            assertEquals(original, text())
        }
    }

    @Test
    fun `the changed lines are the lines the replace texts occupy after every edit`() {
        // Line 4 becomes two lines; the edit above it then adds a line, which moves them to 5 and 6.
        val edits =
            listOf(
                Edit(app, "greet(name);", "greet(name);\n        log(name);"),
                Edit(app, "    void run() {\n", "    // runs\n    void run() {\n"),
            )

        val result = assertIs<EditResult.Applied>(applyEdits(repo, edits))

        assertEquals(mapOf(app to setOf(2, 3, 5, 6)), result.changedLines, "a final line break ends its line, the next isn't changed")
    }

    @Test
    fun `an empty replace text changes the line where its search text began`() {
        val result = assertIs<EditResult.Applied>(applyEdits(repo, listOf(Edit(app, "        String name = \"lector\";\n", ""))))

        assertEquals(mapOf(app to setOf(3)), result.changedLines)
    }

    @Test
    fun `a later edit inside an earlier replace text keeps the rest of it changed`() {
        val edits = listOf(Edit(app, "greet(name);", "a();\n        b();\n        c();"), Edit(app, "b();", "d();"))

        val result = assertIs<EditResult.Applied>(applyEdits(repo, edits))

        assertEquals(mapOf(app to setOf(4, 5, 6)), result.changedLines)
    }

    @Test
    fun `a later edit that replaces an earlier replace text and more marks only its own lines`() {
        val edits =
            listOf(Edit(app, "lector", "Lector"), Edit(app, "String name = \"Lector\";\n        greet(name);", "greet(\"Lector\");"))

        val result = assertIs<EditResult.Applied>(applyEdits(repo, edits))

        assertEquals(mapOf(app to setOf(3)), result.changedLines)
    }

    @Test
    fun `a tolerant match changes the lines of its replace text`() {
        val result = assertIs<EditResult.Applied>(applyEdits(repo, listOf(Edit(app, "greet(name);   \n", "    greet(name, 1);\n"))))

        assertEquals(mapOf(app to setOf(4)), result.changedLines)
    }

    @Test
    fun `a search text that isn't there shows the earliest window with the most equal lines`() {
        repo.resolve(app).writeText("a();\nb();\nx();\na();\nb();\n")

        val result = applyEdits(repo, listOf(Edit(app, "  a();\nb();  \nc();", "d();")))

        assertEquals(
            EditResult.Failed(
                "edit 1 ($app): the search text is not in the file. The most similar lines of the file:\n1: a();\n2: b();\n3: x();",
            ),
            result,
        )
    }

    @Test
    fun `blank lines alike don't make lines similar`() {
        repo.resolve(app).writeText("a();\n\nb();\n")

        val result = applyEdits(repo, listOf(Edit(app, "x();\n\ny();", "z();")))

        assertEquals(EditResult.Failed("edit 1 ($app): the search text is not in the file"), result)
    }

    @Test
    fun `the most similar lines are at most twenty, and no more than the file has`() {
        repo.resolve(app).writeText((1..30).joinToString("") { "line$it();\n" })
        val long = (1..25).joinToString("\n") { "line$it();" } + "\nnot();"

        val reason = assertIs<EditResult.Failed>(applyEdits(repo, listOf(Edit(app, long, "x();")))).reason

        assertEquals((1..20).map { "$it: line$it();" }, reason.lines().drop(1))

        repo.resolve(app).writeText("line1();\n")
        val short = assertIs<EditResult.Failed>(applyEdits(repo, listOf(Edit(app, "line1();\nline2();", "x();")))).reason
        assertEquals(listOf("1: line1();"), short.lines().drop(1))
    }

    @Test
    fun `several matches name the first five lines and count the rest`() {
        repo.resolve(app).writeText("call();\n".repeat(7))

        val result = applyEdits(repo, listOf(Edit(app, "call();", "done();")))

        assertEquals(
            EditResult.Failed(
                "edit 1 ($app): the search text matches 7 times, at lines 1, 2, 3, 4, 5 and 2 more; it must match exactly once",
            ),
            result,
        )
    }

    @Test
    fun `matches that only the earlier edits made are named in the edited file`() {
        val edits = listOf(Edit(app, "greet(name);", "greet(name);\n        greet(name);"), Edit(app, "greet(name);", "x();"))

        val result = applyEdits(repo, edits)

        assertEquals(EditResult.Failed("edit 2 ($app): the search text matches 2 times, at lines 4, 5; it must match exactly once"), result)
    }

    @Test
    fun `a missing file names the one tracked file of that name`() {
        val tracked = listOf("src/main/App.java", "src/test/AppTest.java", "a/Twice.java", "b/Twice.java")

        assertEquals(
            "edit 2 (App.java): no such file in the repository; the repository's file of that name is src/main/App.java",
            missingFile(repo, listOf(Edit(app, "a", "b"), Edit("App.java", "a", "b")), tracked),
        )
        assertEquals(
            "edit 1 (Twice.java): no such file in the repository",
            missingFile(repo, listOf(Edit("Twice.java", "a", "b")), tracked),
        )
        assertEquals("edit 1 (New.java): no such file in the repository", missingFile(repo, listOf(Edit("New.java", "a", "b")), tracked))
    }

    @Test
    fun `files that exist, and paths outside the repository or in git, are not missing files`() {
        val outside = repo.parent.resolve("Nowhere.java").toString()
        listOf(app, "src", "../Nowhere.java", outside, ".git/nowhere").forEach { file ->
            assertNull(missingFile(repo, listOf(Edit(file, "a", "b")), listOf(app)), file)
        }
    }

    private fun text() = repo.resolve(app).readText()
}
