package gmbh.vanella.lector.benchmark

import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.runProcess
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

private val GIT_TIMEOUT = 10.minutes

/**
 * Fetches only the case's base and head commits from its repository into a new repository in `dir`, and checks out
 * the head. Hosts like GitHub serve any commit by its hash, a closed PR's head included.
 */
object GitCheckout : Checkout {
    override fun checkout(
        case: Case,
        dir: Path,
    ): Path {
        val repo = dir.resolve(case.id)
        git(dir, "init", "--quiet", repo.toString())
        git(repo, "fetch", "--quiet", "--depth=1", case.repository, case.head, case.base)
        git(repo, "checkout", "--quiet", "--detach", case.head)
        return repo
    }
}

/** The lines (added plus removed) [commit] changed in [repo]. Binary files count as none. */
fun changedLines(
    repo: Path,
    commit: String,
): Int =
    git(repo, "show", "--numstat", "--format=", commit)
        .lines()
        .filter { it.isNotBlank() }
        .sumOf { line -> line.split('\t').take(2).sumOf { it.toIntOrNull() ?: 0 } }

private fun git(
    dir: Path,
    vararg args: String,
): String {
    val result = runProcess(listOf("git", *args), dir, GIT_TIMEOUT)
    if (result.exitCode != 0) throw ToolError("git ${args.first()} failed in $dir: ${result.output.trim()}")
    return result.output
}
