package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * One search/replace edit the LLM proposes (rule 3: never whole files). [file] is relative to the repository root, with
 * `/` as separator. [search] must match exactly one place in the file.
 */
data class Edit(
    val file: String,
    val search: String,
    val replace: String,
)

private val whitespace = Regex("""\s+""")

/**
 * Applies [edits] to [repo] in order, each to the result of the ones before. Returns why the attempt is rejected, or
 * null if every edit applied.
 *
 * A search text must match exactly once: first as it is, then, if it isn't there, line by line with whitespace ignored
 * (indentation, trailing blanks, runs of spaces). An ambiguous or missing match is never guessed. If any edit doesn't
 * apply, no file is written (rule 4: reject, don't repair).
 */
fun applyEdits(
    repo: Path,
    edits: List<Edit>,
): String? {
    val contents = mutableMapOf<Path, String>()
    for (edit in edits) {
        val path = repoFile(repo, edit.file) ?: return "${edit.file}: no such file in the repository"
        val text = contents.getOrPut(path) { path.readText() }
        contents[path] = replace(text, edit.search, edit.replace).getOrElse { return "${edit.file}: ${it.message}" }
    }
    contents.forEach { (path, text) -> path.writeText(text) }
    return null
}

/** [file] in [repo], if it is a regular file inside the repository and outside `.git`. */
internal fun repoFile(
    repo: Path,
    file: String,
): Path? {
    val root = repo.toAbsolutePath().normalize()
    val path = root.resolve(file).normalize()
    val inside = path.startsWith(root) && !path.startsWith(root.resolve(".git"))
    return if (inside && path.isRegularFile()) path else null
}

private fun replace(
    text: String,
    search: String,
    replace: String,
): Result<String> {
    if (search.isBlank()) return failure("the search text is empty")
    val exact = occurrences(text, search)
    if (exact.size == 1) return Result.success(text.replaceRange(exact.single(), exact.single() + search.length, replace))
    if (exact.size > 1) return failure("the search text matches ${exact.size} times, it must match exactly once")
    return replaceIgnoringWhitespace(text, search, replace)
}

private fun replaceIgnoringWhitespace(
    text: String,
    search: String,
    replace: String,
): Result<String> {
    val lines = text.split("\n")
    val wanted =
        search
            .split("\n")
            .map(::normalize)
            .dropWhile { it.isEmpty() }
            .dropLastWhile { it.isEmpty() }
    val normalized = lines.map(::normalize)
    val starts = (0..lines.size - wanted.size).filter { start -> wanted.indices.all { normalized[start + it] == wanted[it] } }
    return when (starts.size) {
        0 -> {
            failure("the search text is not in the file")
        }

        1 -> {
            Result.success(
                (lines.take(starts.single()) + replace.removeSuffix("\n") + lines.drop(starts.single() + wanted.size)).joinToString("\n"),
            )
        }

        else -> {
            failure("the search text matches ${starts.size} times when whitespace is ignored, it must match exactly once")
        }
    }
}

/** Where [search] starts in [text], overlapping matches included. */
private fun occurrences(
    text: String,
    search: String,
): List<Int> = generateSequence(text.indexOf(search)) { text.indexOf(search, it + 1) }.takeWhile { it >= 0 }.toList()

private fun normalize(line: String) = line.trim().replace(whitespace, " ")

private fun failure(reason: String) = Result.failure<String>(IllegalArgumentException(reason))
