package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

private val GIT_TIMEOUT = 5.minutes

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

    // `XY path`: two status letters and a space.
    private const val STATUS_PREFIX = 3

    private fun git(
        repo: Path,
        vararg args: String,
    ): String {
        val result = runProcess(listOf("git", *args), repo, GIT_TIMEOUT)
        if (result.exitCode != 0) throw ToolError("git ${args.first()} failed in $repo: ${result.output.trim()}")
        return result.output
    }
}
