package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.io.path.readLines

/** How many lines before and after an error a snippet shows. */
private const val CONTEXT_LINES = 40

/** Lines [firstLine] to [lastLine] (1-based, inclusive) of [file], each with its line break. */
data class Snippet(
    val file: String,
    val firstLine: Int,
    val lastLine: Int,
    val text: String,
)

/**
 * The code the model sees for [diagnostics]: ±40 lines around each error, never a whole file longer than that.
 * Overlapping windows in one file become one snippet. An error without a line gets the start of its file.
 *
 * A build error without a location (a missing artifact, an unreadable parent) points to no file. For those, every file in
 * [buildFiles] that contains [anchor] (the new version) gets the window around the first line that has it.
 */
fun codeSnippets(
    repo: Path,
    diagnostics: List<Diagnostic>,
    buildFiles: List<String>,
    anchor: String,
): List<Snippet> {
    val located = diagnostics.mapNotNull { diagnostic -> diagnostic.file?.let { it to diagnostic.line } }
    val buildErrors = diagnostics.any { it.file == null && it.test == null }
    val anchored = if (buildErrors) buildFiles.mapNotNull { file -> anchorLine(repo, file, anchor)?.let { file to it } } else emptyList()
    return (located + anchored)
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .flatMap { (file, lines) -> snippets(repo, file, lines) }
}

private fun anchorLine(
    repo: Path,
    file: String,
    anchor: String,
): Int? {
    val path = repoFile(repo, file) ?: return null
    val index = path.readLines().indexOfFirst { anchor in it }
    return if (index < 0) null else index + 1
}

private fun snippets(
    repo: Path,
    file: String,
    errorLines: List<Int?>,
): List<Snippet> {
    val lines = (repoFile(repo, file) ?: return emptyList()).readLines()
    val windows =
        errorLines
            .map { line -> if (line == null) 1..(2 * CONTEXT_LINES + 1) else (line - CONTEXT_LINES)..(line + CONTEXT_LINES) }
            .map { maxOf(it.first, 1)..minOf(it.last, lines.size) }
            .sortedBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (window in windows) {
        val last = merged.lastOrNull()
        if (last != null && window.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, window.last)
        } else {
            merged += window
        }
    }
    return merged.map { range -> Snippet(file, range.first, range.last, range.joinToString("") { lines[it - 1] + "\n" }) }
}
