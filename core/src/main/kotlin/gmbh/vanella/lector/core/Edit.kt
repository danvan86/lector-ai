package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
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

/** What [applyEdits] did. */
sealed interface EditResult {
    /**
     * Every edit applied. [changedLines] are, per file (relative to the repository root), the lines its replace texts
     * occupy after the edits (1-based). An empty replace text changes the line where its search text began.
     */
    data class Applied(
        val changedLines: Map<String, Set<Int>>,
    ) : EditResult

    /** No file was written, for [reason]: an edit that doesn't apply names its number, its file, and what the file has. */
    data class Failed(
        val reason: String,
    ) : EditResult
}

private val whitespace = Regex("""\s+""")

// The most lines shown as the most similar to a search text that isn't in the file.
private const val MAX_SIMILAR_LINES = 20

// The most line numbers of matches a reason lists. A long list crowds out the rest of the request.
private const val MAX_MATCH_LINES = 5

/**
 * Applies [edits] to [repo] in order, each to the result of the ones before.
 *
 * A search text must match exactly once: first as it is, then, if it isn't there, line by line with whitespace ignored
 * (indentation, trailing blanks, runs of spaces). An ambiguous or missing match is never guessed. If any edit doesn't
 * apply, or the edits change nothing, no file is written (rule 4: reject, don't repair). The reason shows what the file
 * has instead, with the line numbers of the committed file, which the model saw.
 */
fun applyEdits(
    repo: Path,
    edits: List<Edit>,
): EditResult {
    val root = repo.toAbsolutePath().normalize()
    val files = mutableMapOf<Path, EditedFile>()
    for ((index, edit) in edits.withIndex()) {
        val path = repoFile(repo, edit.file) ?: return EditResult.Failed("edit ${index + 1} (${edit.file}): no such file in the repository")
        val file = files.getOrPut(path) { EditedFile(path.readText()) }
        file.apply(edit)?.let { return EditResult.Failed("edit ${index + 1} (${edit.file}): $it") }
    }
    // Verifying edits that change nothing would cost a whole build to learn that.
    if (files.values.all { it.text == it.original }) return EditResult.Failed("the edits change nothing")
    files.forEach { (path, file) -> path.writeText(file.text) }
    return EditResult.Applied(
        files
            .map { (path, file) ->
                root.relativize(path).invariantSeparatorsPathString to file.changedLines()
            }.toMap(),
    )
}

/**
 * Why an edit of [edits] can't be applied because its file doesn't exist in [repo], or null if each one's does. Names
 * the file of [tracked] with the same name, if there is exactly one. Paths outside the repository are the scope's.
 */
fun missingFile(
    repo: Path,
    edits: List<Edit>,
    tracked: List<String>,
): String? {
    val root = repo.toAbsolutePath().normalize()
    val (index, edit) =
        edits.withIndex().firstOrNull { (_, edit) ->
            val path = root.resolve(edit.file).normalize()
            path.startsWith(root) && !path.startsWith(root.resolve(".git")) && !path.exists()
        } ?: return null
    val name = edit.file.substringAfterLast('/')
    val namesake = tracked.singleOrNull { it.substringAfterLast('/') == name }
    val hint = if (namesake == null) "" else "; the repository's file of that name is $namesake"
    return "edit ${index + 1} (${edit.file}): no such file in the repository$hint"
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

/** A file while an answer's edits are applied: its [original] text, the edited [text], and where the replace texts are. */
private class EditedFile(
    val original: String,
) {
    var text = original
        private set

    // The characters the replace texts occupy in [text], end exclusive. An empty one marks where a deletion was.
    private var spans = listOf<IntRange>()

    /** Applies [edit] to [text]. Returns why it can't, or null. */
    fun apply(edit: Edit): String? =
        when (val match = find(text, edit.search)) {
            is Match.Found -> {
                val replacement = if (match.wholeLines) edit.replace.removeSuffix("\n") else edit.replace
                replace(match.start, match.length, replacement)
                null
            }

            is Match.Several -> {
                // The committed file's lines, as the model saw them, unless the earlier edits made the matches.
                val lines = (find(original, edit.search) as? Match.Several)?.lines ?: match.lines
                "the search text matches ${lines.size} times, at lines ${listed(lines)}; it must match exactly once"
            }

            is Match.Missing -> {
                "the search text is not in the file" + similar(original, edit.search)
            }

            is Match.Empty -> {
                "the search text is empty"
            }
        }

    /** The lines of [text] the replace texts occupy, 1-based. */
    fun changedLines(): Set<Int> =
        spans
            .flatMap { span ->
                if (span.isEmpty()) listOf(lineAt(span.first)) else (lineAt(span.first)..lineAt(span.last)).toList()
            }.toSortedSet()

    private fun replace(
        start: Int,
        length: Int,
        replacement: String,
    ) {
        text = text.replaceRange(start, start + length, replacement)
        val end = start + length
        val shift = replacement.length - length
        // An earlier replace text keeps what this one doesn't replace of it.
        spans =
            spans.flatMap { span ->
                val after = span.last + 1
                when {
                    after <= start -> {
                        listOf(span)
                    }

                    span.first >= end -> {
                        listOf(span.first + shift until after + shift)
                    }

                    else -> {
                        listOfNotNull(
                            (span.first until start).takeIf { span.first < start },
                            (end + shift until after + shift).takeIf {
                                after >
                                    end
                            },
                        )
                    }
                }
            } + listOf(start until start + replacement.length)
    }

    // A replace text's final line break ends its last line, so the character before it decides.
    private fun lineAt(index: Int) = 1 + text.take(index).count { it == '\n' }
}

private sealed interface Match {
    /** The one match: [length] characters at [start]. [wholeLines] if it matched line by line, without the last line break. */
    data class Found(
        val start: Int,
        val length: Int,
        val wholeLines: Boolean,
    ) : Match

    /** More than one match, starting at [lines] (1-based). */
    data class Several(
        val lines: List<Int>,
    ) : Match

    data object Missing : Match

    data object Empty : Match
}

private fun find(
    text: String,
    search: String,
): Match {
    if (search.isBlank()) return Match.Empty
    val exact = occurrences(text, search)
    if (exact.size == 1) return Match.Found(exact.single(), search.length, wholeLines = false)
    if (exact.size > 1) return Match.Several(exact.map { start -> 1 + text.take(start).count { it == '\n' } })
    val lines = text.split("\n")
    val wanted = wantedLines(search)
    val normalized = lines.map(::normalize)
    val starts = (0..lines.size - wanted.size).filter { start -> wanted.indices.all { normalized[start + it] == wanted[it] } }
    return when (starts.size) {
        0 -> {
            Match.Missing
        }

        1 -> {
            val first = starts.single()
            val offset = lines.take(first).sumOf { it.length + 1 }
            Match.Found(offset, lines.subList(first, first + wanted.size).joinToString("\n").length, wholeLines = true)
        }

        else -> {
            Match.Several(starts.map { it + 1 })
        }
    }
}

/**
 * The lines of [text] most similar to [search], with their numbers: the window of consecutive lines, as long as the
 * search text but at most [MAX_SIMILAR_LINES], in which the most of the search text's lines equal the window's line at
 * the same position, indentation and whitespace at line ends ignored. Ties: the earliest window. Empty if no line equals.
 */
private fun similar(
    text: String,
    search: String,
): String {
    val wanted = wantedLines(search).take(MAX_SIMILAR_LINES)
    val lines = text.removeSuffix("\n").split("\n")
    val normalized = lines.map(::normalize)
    val size = minOf(wanted.size, lines.size)
    val (start, equal) =
        (0..lines.size - size)
            .map { start -> start to (0 until size).count { wanted[it].isNotEmpty() && normalized[start + it] == wanted[it] } }
            .maxBy { it.second }
    if (equal == 0) return ""
    val window = (start until start + size).joinToString("\n") { "${it + 1}: ${lines[it]}" }
    return ". The most similar lines of the file:\n$window"
}

/** The lines of [search] as the tolerant match compares them, without the blank lines around them. */
private fun wantedLines(search: String) =
    search
        .split("\n")
        .map(::normalize)
        .dropWhile { it.isEmpty() }
        .dropLastWhile { it.isEmpty() }

/** `1, 2, 3, 4, 5 and 3 more`. */
private fun listed(lines: List<Int>): String {
    val more = lines.size - MAX_MATCH_LINES
    return lines.take(MAX_MATCH_LINES).joinToString(", ") + if (more > 0) " and $more more" else ""
}

/** Where [search] starts in [text], overlapping matches included. */
private fun occurrences(
    text: String,
    search: String,
): List<Int> = generateSequence(text.indexOf(search)) { text.indexOf(search, it + 1) }.takeWhile { it >= 0 }.toList()

private fun normalize(line: String) = line.trim().replace(whitespace, " ")
