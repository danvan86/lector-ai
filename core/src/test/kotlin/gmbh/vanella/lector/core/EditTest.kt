package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

        assertNull(result)
        assertEquals(original.replace("greet(name);", "welcome(name);"), text())
    }

    @Test
    fun `a match that differs only in whitespace is replaced through the tolerant match`() {
        val search = "\n  void run() {\n      String  name = \"lector\";\t\n"
        val replace = "    void run() {\n        String name = \"Lector\";"

        val result = applyEdits(repo, listOf(Edit(app, search, replace)))

        assertNull(result)
        assertEquals(original.replace("\"lector\"", "\"Lector\""), text())
    }

    @Test
    fun `several edits apply in order, each to the result of the one before`() {
        val edits = listOf(Edit(app, "greet(name);", "welcome(name);"), Edit(app, "welcome(name);", "welcome(name, 1);"))

        val result = applyEdits(repo, edits)

        assertNull(result)
        assertEquals(original.replace("greet(name);", "welcome(name, 1);"), text())
    }

    @Test
    fun `an ambiguous exact match rejects the whole attempt, and no edit is written`() {
        val edits = listOf(Edit(app, "class App", "class Main"), Edit(app, "name", "user"))

        val result = applyEdits(repo, edits)

        assertEquals("$app: the search text matches 2 times, it must match exactly once", result)
        assertEquals(original, text())
    }

    @Test
    fun `an ambiguous tolerant match rejects the attempt`() {
        repo.resolve(app).writeText("a();\n  b();\na();\n b();\n")

        val result = applyEdits(repo, listOf(Edit(app, "a();\nb();", "c();")))

        assertEquals("$app: the search text matches 2 times when whitespace is ignored, it must match exactly once", result)
    }

    @Test
    fun `a search text that isn't there rejects the attempt`() {
        val result = applyEdits(repo, listOf(Edit(app, "farewell(name);", "bye(name);")))

        assertEquals("$app: the search text is not in the file", result)
        assertEquals(original, text())
    }

    @Test
    fun `an empty search text rejects the attempt`() {
        val result = applyEdits(repo, listOf(Edit(app, " \n", "class Other {}")))

        assertEquals("$app: the search text is empty", result)
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

            assertEquals("$file: no such file in the repository", result)
            assertEquals(original, text())
        }
    }

    private fun text() = repo.resolve(app).readText()
}
