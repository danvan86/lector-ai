package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class GitBaseCommitTest {
    @TempDir
    lateinit var repo: Path

    @BeforeTest
    fun `create a repository with a base and a head commit`() {
        git("init", "--quiet")
        repo.resolve("version.txt").writeText("base")
        git("add", ".")
        git("commit", "--quiet", "-m", "base")
        repo.resolve("version.txt").writeText("head")
        git("commit", "--quiet", "-am", "head")
    }

    @Test
    fun `checks out the base commit in a separate worktree and removes it afterwards`() {
        var checkout: Path? = null

        val result =
            GitBaseCommit.resolve(repo, "HEAD~1").checkout { dir ->
                checkout = dir
                assertEquals("base", dir.resolve("version.txt").readText())
                assertEquals(2, worktrees())
                emptyList()
            }

        assertEquals(emptyList(), result)
        assertEquals("head", repo.resolve("version.txt").readText())
        assertEquals(1, worktrees())
        assertFalse(checkout!!.exists())
    }

    @Test
    fun `the worktree is removed when the block fails`() {
        assertFailsWith<IllegalStateException> {
            GitBaseCommit.resolve(repo, "HEAD~1").checkout { error("build crashed") }
        }

        assertEquals(1, worktrees())
    }

    @Test
    fun `returns what the block returns`() {
        val diagnostic = Diagnostic(null, null, null, null, "broken", Severity.ERROR, "test")

        assertEquals(listOf(diagnostic), GitBaseCommit.resolve(repo, "HEAD~1").checkout { listOf(diagnostic) })
    }

    @Test
    fun `an unknown ref is a tool error`() {
        val error = assertFailsWith<ToolError> { GitBaseCommit.resolve(repo, "no-such-branch") }

        assertEquals("Base ref no-such-branch is not a commit in $repo", error.message)
    }

    @Test
    fun `a directory without Git is a tool error that says why`(
        @TempDir noGit: Path,
    ) {
        val error = assertFailsWith<ToolError> { GitBaseCommit.resolve(noGit, "HEAD") }

        assertContains(error.message.orEmpty(), "Base ref HEAD is not a commit in $noGit: fatal: not a git repository")
    }

    @Test
    fun `a worktree that can't be removed is a tool error`() {
        val error =
            assertFailsWith<ToolError> {
                GitBaseCommit.resolve(repo, "HEAD~1").checkout { dir ->
                    git("worktree", "lock", dir.toString())
                    emptyList()
                }
            }

        assertContains(error.message.orEmpty(), "git worktree remove failed")
    }

    private fun worktrees() = git("worktree", "list").lines().count { it.isNotBlank() }

    private fun git(vararg args: String): String {
        val command = listOf("git", "-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
        val result = runProcess(command + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }
}
