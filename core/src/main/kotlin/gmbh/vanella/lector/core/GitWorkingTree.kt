package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

private val GIT_TIMEOUT = 5.minutes

/** Who commits: the tool's own identity on the command line, the Git host's bot in a pipeline. */
data class CommitIdentity(
    val name: String,
    val email: String,
) {
    companion object {
        val TOOL = CommitIdentity("lector", "lector@localhost")
    }
}

/**
 * The repository's working tree, seen through Git. A run starts on a clean tree, so [reset] can never destroy anyone's
 * work. Ignored files (build output) are neither reported nor removed.
 */
object GitWorkingTree {
    /** Fails with a [ToolError] if the working tree has uncommitted changes, or isn't a Git working tree. */
    fun requireClean(repo: Path) {
        val changed = changedFiles(repo)
        if (changed.isNotEmpty()) {
            throw ToolError("$repo has uncommitted changes. Commit or stash them first: ${changed.joinToString(", ")}")
        }
    }

    /** The modified and new files, relative to the repository root, sorted. */
    fun changedFiles(repo: Path): List<String> =
        git(repo, "status", "--porcelain", "-z", "--untracked-files=all")
            .split('\u0000')
            .filter { it.isNotEmpty() }
            .map { it.drop(STATUS_PREFIX) }
            .sorted()

    /** Restores the tracked files to `HEAD` and removes the new files. */
    fun reset(repo: Path) {
        git(repo, "reset", "--hard", "--quiet")
        git(repo, "clean", "-d", "--force", "--quiet")
    }

    /** The files Git tracks, relative to the repository root, sorted. */
    fun trackedFiles(repo: Path): List<String> =
        git(repo, "ls-files", "-z")
            .split('\u0000')
            .filter { it.isNotEmpty() }
            .sorted()

    /**
     * Commits the changes to [files] (relative to the repository root, new and deleted ones included) as [identity], author
     * and committer, and returns the commit. Anything else in the working tree, like build output, stays out of it. The
     * repository's hooks don't run, and nothing is signed: a CI run can't answer a prompt.
     */
    fun commit(
        repo: Path,
        message: String,
        files: List<String>,
        identity: CommitIdentity = CommitIdentity.TOOL,
    ): String {
        if (files.isNotEmpty()) git(repo, listOf("add", "--all", "--") + files)
        val config = listOf("-c", "user.name=${identity.name}", "-c", "user.email=${identity.email}", "-c", "commit.gpgsign=false")
        git(repo, config + listOf("commit", "--quiet", "--no-verify", "--allow-empty", "-m", message))
        return head(repo)
    }

    /** The commit the working tree is on. */
    fun head(repo: Path): String = git(repo, "rev-parse", "HEAD").trim()

    /** The author's email of [commit]. */
    fun authorEmail(
        repo: Path,
        commit: String,
    ): String = git(repo, "log", "-1", "--format=%ae", commit).trim()

    /** Whether [commit] is in the repository, e.g. not cut off by a shallow clone. */
    fun hasCommit(
        repo: Path,
        commit: String,
    ): Boolean = runProcess(listOf("git", "cat-file", "-e", "$commit^{commit}"), repo, GIT_TIMEOUT).exitCode == 0

    /** The changes [commit] made, as a unified diff. */
    fun diff(
        repo: Path,
        commit: String,
    ): String = git(repo, "show", "--format=", "--no-color", commit)

    // `XY path`: two status letters and a space.
    private const val STATUS_PREFIX = 3

    private fun git(
        repo: Path,
        vararg args: String,
    ) = git(repo, args.asList())

    private fun git(
        repo: Path,
        args: List<String>,
    ): String {
        val result = runProcess(listOf("git") + args, repo, GIT_TIMEOUT)
        val command = args.first { !it.startsWith("-") && '=' !in it }
        if (result.exitCode != 0) throw ToolError("git $command failed in $repo: ${result.output.trim()}")
        return result.output
    }
}
