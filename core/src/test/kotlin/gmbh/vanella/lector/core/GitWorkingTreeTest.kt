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

    @Test
    fun `commit records every change with the tool's identity, and diff shows what it changed`() {
        repo.resolve("pom.txt").writeText("v2\n")
        repo.resolve("New.java").writeText("class New {}\n")

        val commit = GitWorkingTree.commit(repo, "Raise the version\n\nBecause.", listOf("pom.txt", "New.java"))

        assertEquals(git("rev-parse", "HEAD").trim(), commit)
        assertEquals("lector <lector@localhost>|Raise the version|Because.", git("log", "-1", "--format=%an <%ae>|%s|%b").trim())
        assertEquals("", git("status", "--porcelain"))
        val diff = GitWorkingTree.diff(repo, commit)
        assertContains(diff, "+++ b/New.java")
        assertContains(diff, "-v1\n+v2")
        assertFalse("Raise the version" in diff, "only the changes, not the message")
    }

    @Test
    fun `commit records only the given files and leaves the rest of the working tree as it is`() {
        repo.resolve("pom.txt").writeText("v2\n")
        repo.resolve("test-output.log").writeText("written by the build\n")

        val commit = GitWorkingTree.commit(repo, "Raise the version", listOf("pom.txt"))

        assertEquals("pom.txt", git("show", "--name-only", "--format=", commit).trim())
        assertEquals("?? test-output.log", git("status", "--porcelain").trim())
    }

    @Test
    fun `commit without files is an empty commit`() {
        repo.resolve("test-output.log").writeText("written by the build\n")

        val commit = GitWorkingTree.commit(repo, "Nothing to record", emptyList())

        assertEquals("", git("show", "--name-only", "--format=", commit).trim())
        assertEquals("Nothing to record", git("log", "-1", "--format=%s").trim())
    }

    @Test
    fun `lists the tracked files, but not ignored or untracked ones`() {
        repo.resolve("notes.txt").writeText("draft\n")

        assertEquals(listOf(".gitignore", "pom.txt"), GitWorkingTree.trackedFiles(repo))
    }

    @Test
    fun `a failing commit is a tool error that names the command`(
        @TempDir noGit: Path,
    ) {
        val error = assertFailsWith<ToolError> { GitWorkingTree.diff(noGit, "HEAD") }

        assertContains(error.message, "git show failed")
    }

    private fun git(vararg args: String): String {
        val command = listOf("git", "-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
        val result = runProcess(command + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }
}
