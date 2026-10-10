package gmbh.vanella.lector.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration.Companion.minutes

private val GIT_TIMEOUT = 5.minutes

/**
 * A base commit of a Git repository. [checkout] builds it in a separate worktree in a temporary directory, so the
 * repository's own working tree stays untouched, and removes the worktree afterwards.
 */
class GitBaseCommit private constructor(
    private val repo: Path,
    private val commit: String,
) : BaseCommit {
    @OptIn(ExperimentalPathApi::class)
    override fun checkout(block: (Path) -> List<Diagnostic>): List<Diagnostic> {
        val dir = Files.createTempDirectory("lector-base")
        val worktree = dir.resolve("base")
        try {
            git(repo, "worktree", "add", "--detach", worktree.toString(), commit)
            try {
                return block(worktree)
            } finally {
                git(repo, "worktree", "remove", "--force", worktree.toString())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    companion object {
        /** Resolves [ref] to a commit of [repo]. Fails with a [ToolError] if it is none. */
        fun resolve(
            repo: Path,
            ref: String,
        ): GitBaseCommit {
            val result = runProcess(listOf("git", "rev-parse", "--verify", "--quiet", "$ref^{commit}"), repo, GIT_TIMEOUT)
            if (result.exitCode != 0) {
                val reason = result.output.trim().let { if (it.isEmpty()) "" else ": $it" }
                throw ToolError("Base ref $ref is not a commit in $repo$reason")
            }
            return GitBaseCommit(repo, result.output.trim())
        }
    }
}

private fun git(
    repo: Path,
    vararg args: String,
) {
    val result = runProcess(listOf("git", *args), repo, GIT_TIMEOUT)
    if (result.exitCode != 0) throw ToolError("git ${args.take(2).joinToString(" ")} failed: ${result.output.trim()}")
}
