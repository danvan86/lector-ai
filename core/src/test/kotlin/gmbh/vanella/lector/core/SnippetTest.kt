package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SnippetTest {
    @TempDir
    lateinit var repo: Path

    private val big = "src/Big.java"

    @BeforeTest
    fun `create a 500-line file`() {
        repo
            .resolve("src")
            .createDirectories()
            .resolve("Big.java")
            .writeText(lines(1..500))
    }

    @Test
    fun `an error in a 500-line file gets at most 81 lines around it`() {
        val snippets = codeSnippets(repo, listOf(error(big, 250)), buildFiles = emptyList(), anchor = "2.0")

        assertEquals(listOf(Snippet(big, 210, 290, lines(210..290))), snippets)
        assertEquals(81, snippets.single().text.count { it == '\n' })
    }

    @Test
    fun `the window is cut at the start and end of the file`() {
        val snippets = codeSnippets(repo, listOf(error(big, 3), error(big, 495)), buildFiles = emptyList(), anchor = "2.0")

        assertEquals(listOf(Snippet(big, 1, 43, lines(1..43)), Snippet(big, 455, 500, lines(455..500))), snippets)
    }

    @Test
    fun `overlapping windows in one file are merged into one snippet`() {
        val snippets =
            codeSnippets(repo, listOf(error(big, 100), error(big, 150), error(big, 100)), buildFiles = emptyList(), anchor = "2.0")

        assertEquals(listOf(Snippet(big, 60, 190, lines(60..190))), snippets)
    }

    @Test
    fun `an error without a line gets the start of its file`() {
        val snippets = codeSnippets(repo, listOf(error(big, null)), buildFiles = emptyList(), anchor = "2.0")

        assertEquals(listOf(Snippet(big, 1, 81, lines(1..81))), snippets)
    }

    @Test
    fun `files that aren't in the repository get no snippet`() {
        val outside = repo.parent.resolve("Outside.java").toString()

        val snippets = codeSnippets(repo, listOf(error("src/Gone.java", 1), error(outside, 1), error("src", 1)), emptyList(), "2.0")

        assertEquals(emptyList(), snippets)
    }

    @Test
    fun `a build error without a location gets the build files around the line with the new version`() {
        repo.resolve("pom.xml").writeText(lines(1..99) + "<version>2.0</version>\n" + lines(101..300))
        repo
            .resolve("module")
            .createDirectories()
            .resolve("pom.xml")
            .writeText("<version>1.0</version>\n")
        val buildError = Diagnostic(null, null, null, null, "Could not find artifact org.example:lib:jar:2.0", Severity.ERROR, "maven")

        val snippets = codeSnippets(repo, listOf(buildError, error(big, 1)), listOf("module/pom.xml", "pom.xml"), anchor = "2.0")

        val pom = lines(60..99) + "<version>2.0</version>\n" + lines(101..140)
        assertEquals(listOf(Snippet("pom.xml", 60, 140, pom), Snippet(big, 1, 41, lines(1..41))), snippets)
    }

    @Test
    fun `a build file that is missing or doesn't have the new version gets no snippet`() {
        repo.resolve("pom.xml").writeText("<version>1.0</version>\n")
        val buildError = Diagnostic(null, null, null, null, "Could not resolve dependencies", Severity.ERROR, "maven")

        val snippets = codeSnippets(repo, listOf(buildError), listOf("missing/pom.xml", "pom.xml"), anchor = "2.0")

        assertEquals(emptyList(), snippets)
    }

    @Test
    fun `without a build error the build files are not read, and a test failure without a location is none`() {
        repo.resolve("pom.xml").writeText("<version>2.0</version>\n")
        val testFailure = Diagnostic(null, null, null, null, "expected 1", Severity.ERROR, "surefire", "com.example.AppTest#runs")

        val snippets = codeSnippets(repo, listOf(error(big, 1), testFailure), listOf("pom.xml"), anchor = "2.0")

        assertEquals(listOf(big), snippets.map { it.file })
    }

    private fun error(
        file: String,
        line: Int?,
    ) = Diagnostic(file, line, null, null, "cannot find symbol", Severity.ERROR, "javac")

    private fun lines(range: IntRange) = range.joinToString("") { "line $it\n" }
}
