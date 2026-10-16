package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Scope: the model edits only files that Git tracks, inside the repository, and in the adapter's editable scope (rule
 * 5: never edit generated code). The Git check catches generators the adapter doesn't know. It runs on the edits'
 * paths before they are written, because resetting the working tree doesn't restore ignored files.
 */
internal class Scope(
    repo: Path,
    private val tracked: Set<String>,
    private val isEditable: (String) -> Boolean,
) {
    private val root = repo.toAbsolutePath().normalize()

    fun check(files: List<String>): List<Violation> =
        files.distinct().mapNotNull { file ->
            reason(file)?.let {
                Violation("scope", file, null, it)
            }
        }

    private fun reason(file: String): String? {
        val path = root.resolve(file).normalize()
        if (!path.startsWith(root)) return "the file is outside the repository"
        val relative = root.relativize(path).invariantSeparatorsPathString
        return when {
            !isEditable(relative) -> "the file is build output or generated code, which is never edited"
            relative !in tracked -> "Git doesn't track the file, so it is generated or ignored"
            else -> null
        }
    }
}

/** Diff size: an attempt changes at most [limit] lines (added plus removed). More is a rewrite, not a fix. */
internal class DiffSize(
    private val limit: Int,
) : Guardrail {
    override fun check(attempt: AttemptChanges): List<Violation> {
        val changed = attempt.files.sumOf { it.changedLines }
        if (changed <= limit) return emptyList()
        return listOf(Violation("diff-size", null, null, "the edits change $changed lines (added plus removed), the limit is $limit"))
    }
}

/**
 * Build-file blocklist: no new or changed line in a build file disables tests, and no change loosens it as a whole, by
 * what it removes or narrows. What does is the adapter's knowledge.
 */
internal class BuildFileBlocklist(
    private val isBuildFile: (String) -> Boolean,
    private val testDisablingConfig: (file: String, content: String, line: Int) -> String?,
    private val loosening: (file: String, before: String, after: String) -> List<String>,
) : Guardrail {
    override fun check(attempt: AttemptChanges) =
        attempt.files.filter { isBuildFile(it.file) }.flatMap { change ->
            val lines =
                change.addedLines.mapNotNull { line ->
                    testDisablingConfig(change.file, change.after, line)?.let { Violation("build-file", change.file, line, it) }
                }
            lines + loosening(change.file, change.before, change.after).map { Violation("build-file", change.file, null, it) }
        }
}
