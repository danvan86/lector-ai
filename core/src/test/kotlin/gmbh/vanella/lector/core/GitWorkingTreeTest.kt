package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class GitWorkingTreeTest {
    @TempDir
    lateinit var repo: Path

    @BeforeTest
    fun `create a clean repository that ignores build output`() {
        git("init", "--quiet")
        repo.resolve(".gitignore").writeText("target/\n")
        repo.resolve("pom.txt").writeText("v1\n")
        git("add", ".")
        git("commit", "--quiet", "-m", "head")
    }

    @Test
    fun `a clean working tree passes the check`() {
        GitWorkingTree.requireClean(repo)
    }

    @Test
    fun `ignored files don't make the working tree dirty`() {
        repo
            .resolve("target")
            .createDirectories()
            .resolve("out.txt")
            .writeText("built")

        GitWorkingTree.requireClean(repo)
    }

    @Test
    fun `a modified or untracked file is a tool error that names it`() {
        repo.resolve("pom.txt").writeText("v2\n")
        repo.resolve("notes.txt").writeText("draft\n")

        val error = assertFailsWith<ToolError> { GitWorkingTree.requireClean(repo) }

        assertContains(error.message, "has uncommitted changes")
        assertContains(error.message, "pom.txt")
        assertContains(error.message, "notes.txt")
    }

    @Test
    fun `a directory without Git is a tool error`(
        @TempDir noGit: Path,
    ) {
        val error = assertFailsWith<ToolError> { GitWorkingTree.requireClean(noGit) }

        assertContains(error.message, "git status failed")
    }

    @Test
    fun `lists modified and new files, also in new directories, sorted`() {
        repo.resolve("pom.txt").writeText("v2\n")
        repo
            .resolve("src/main")
            .createDirectories()
            .resolve("New.java")
            .writeText("class New {}\n")
        repo
            .resolve("target")
            .createDirectories()
            .resolve("out.txt")
            .writeText("built")

        assertEquals(listOf("pom.txt", "src/main/New.java"), GitWorkingTree.changedFiles(repo))
    }

    @Test
    fun `lists a file with a space in its name as it is`() {
        repo.resolve("my notes.txt").writeText("draft\n")

        assertEquals(listOf("my notes.txt"), GitWorkingTree.changedFiles(repo))
    }

    @Test
    fun `reset restores tracked files and removes new ones, but keeps ignored files`() {
        repo.resolve("pom.txt").writeText("v2\n")
        repo
            .resolve("src")
            .createDirectories()
            .resolve("New.java")
            .writeText("class New {}\n")
        val built = repo.resolve("target").createDirectories().resolve("out.txt")
        built.writeText("built")

        GitWorkingTree.reset(repo)

        assertEquals("", git("status", "--porcelain"))
        assertFalse(repo.resolve("src").exists())
        assertTrue(built.exists())
    }

    private fun git(vararg args: String): String {
        val command = listOf("git", "-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
        val result = runProcess(command + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }
}
