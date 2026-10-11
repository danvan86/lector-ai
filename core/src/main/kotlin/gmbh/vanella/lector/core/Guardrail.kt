package gmbh.vanella.lector.core

/**
 * One deterministic rule every attempt of the model must pass before it is verified (concept section 7). It sees only
 * the changed files, the facts about them, and the diagnostics. It rejects, it never repairs (rule 4), and it never
 * calls the LLM (rule 2).
 */
fun interface Guardrail {
    /** The rules [attempt] breaks. Empty if it passes. */
    fun check(attempt: AttemptChanges): List<Violation>
}

/** What a guardrail sees of one attempt: the [files] it changed, and the [diagnostics] the update caused before it. */
class AttemptChanges(
    val files: List<FileChange>,
    val diagnostics: List<Diagnostic>,
)

/**
 * A broken [rule], with a [reason] the model can act on. [file] is null if the rule is about the whole attempt, [line]
 * if it is about the whole file.
 */
data class Violation(
    val rule: String,
    val file: String?,
    val line: Int?,
    val reason: String,
) {
    override fun toString(): String {
        val location = file?.let { listOfNotNull(it, line).joinToString(":") + ": " }.orEmpty()
        return "[$rule] $location$reason"
    }
}

/**
 * One file an attempt changed, relative to the repository root: its text [before] (the last commit) and [after] (the
 * working tree), with the facts [languageSupport] reports about each. Null facts: no language there knows the file.
 */
class FileChange(
    val file: String,
    val before: String,
    val after: String,
    languageSupport: LanguageSupport,
) {
    val factsBefore = languageSupport.facts(file, before)
    val factsAfter = languageSupport.facts(file, after)

    val hunks = lineDiff(before.lines(), after.lines())

    /** The lines of [after] that are new or changed. */
    val addedLines = hunks.flatMap { it.added }

    /** Removed plus added lines. */
    val changedLines = hunks.sumOf { it.removed.count() + it.added.count() }

    /** Whether the attempt changed [range] of [after]: a line in it is new, or lines were removed from between two of its lines. */
    fun touches(range: LineRange) =
        hunks.any { hunk ->
            val added = hunk.added
            if (added.isEmpty()) {
                range.first < added.first && added.first <= range.last
            } else {
                added.first <= range.last &&
                    range.first <= added.last
            }
        }
}

/**
 * A run of lines that differ: the [removed] lines of the old text (1-based) became the [added] lines of the new one.
 * An empty range sits right before its `first` line.
 */
data class Hunk(
    val removed: IntRange,
    val added: IntRange,
)

// The largest comparison table (in cells) the line diff builds. Larger changes are one hunk; they are too big anyway.
private const val MAX_TABLE = 4_000_000

/** The lines that differ between [before] and [after]: the lines around their longest common subsequence. */
internal fun lineDiff(
    before: List<String>,
    after: List<String>,
): List<Hunk> {
    val prefix = before.zip(after).takeWhile { (a, b) -> a == b }.size
    val suffix =
        before
            .drop(prefix)
            .asReversed()
            .zip(after.drop(prefix).asReversed())
            .takeWhile { (a, b) -> a == b }
            .size
    val a = before.subList(prefix, before.size - suffix)
    val b = after.subList(prefix, after.size - suffix)
    val common = if (a.size.toLong() * b.size > MAX_TABLE) emptyList() else commonLines(a, b)
    val hunks = mutableListOf<Hunk>()
    var i = 0
    var j = 0
    for ((nextI, nextJ) in common + (a.size to b.size)) {
        if (nextI > i || nextJ > j) hunks += Hunk((prefix + i + 1)..(prefix + nextI), (prefix + j + 1)..(prefix + nextJ))
        i = nextI + 1
        j = nextJ + 1
    }
    return hunks
}

/** The index pairs of a longest common subsequence of [a] and [b], in order. */
private fun commonLines(
    a: List<String>,
    b: List<String>,
): List<Pair<Int, Int>> {
    // lengths[i][j]: the length of the longest common subsequence of a from i and b from j.
    val lengths = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) {
        for (j in b.indices.reversed()) {
            lengths[i][j] = if (a[i] == b[j]) lengths[i + 1][j + 1] + 1 else maxOf(lengths[i + 1][j], lengths[i][j + 1])
        }
    }
    val pairs = mutableListOf<Pair<Int, Int>>()
    var i = 0
    var j = 0
    while (i < a.size && j < b.size) {
        when {
            a[i] == b[j] -> pairs += (i++ to j++)
            lengths[i + 1][j] >= lengths[i][j + 1] -> i++
            else -> j++
        }
    }
    return pairs
}
