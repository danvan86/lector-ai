package gmbh.vanella.lector.core

/** Diagnostics that share one root cause, by the adapter's cluster key, or else by file. They are fixed together. */
data class Cluster(
    val key: String,
    val diagnostics: List<Diagnostic>,
)

private const val NO_LOCATION = "(no location)"

/**
 * Groups [diagnostics] into clusters, in the order they are fixed: clusters with build-file errors first (build errors
 * without a location included), then the largest. One root cause, like a missing artifact, can produce hundreds of
 * follow-on errors, so it goes first (concept section 6.2).
 */
fun clusters(
    diagnostics: List<Diagnostic>,
    adapter: EcosystemAdapter,
): List<Cluster> =
    diagnostics
        .groupBy { adapter.clusterKey(it) ?: it.file ?: NO_LOCATION }
        .map { (key, members) -> Cluster(key, members) }
        .sortedWith(
            compareByDescending<Cluster> { cluster ->
                cluster.diagnostics.any { isBuildError(it, adapter) }
            }.thenByDescending { it.diagnostics.size },
        )

private fun isBuildError(
    diagnostic: Diagnostic,
    adapter: EcosystemAdapter,
): Boolean {
    val file = diagnostic.file ?: return diagnostic.test == null
    return adapter.isBuildFile(file)
}

/**
 * The diagnostics caused by the update, counted per stage: build errors without a location, compile errors, test
 * failures. Lower is better, compared stage by stage in that order. So a fixed missing artifact counts even if it
 * unmasks 50 compile errors, and a new compile error never counts while fixing tests.
 */
data class Progress(
    val buildErrors: Int,
    val compileErrors: Int,
    val testFailures: Int,
) : Comparable<Progress> {
    override fun compareTo(other: Progress) = compareValuesBy(this, other, { it.buildErrors }, { it.compileErrors }, { it.testFailures })

    override fun toString() = "$buildErrors build errors, $compileErrors compile errors, $testFailures test failures"

    companion object {
        fun of(diagnostics: List<Diagnostic>) =
            Progress(
                buildErrors = diagnostics.count { it.file == null && it.test == null },
                compileErrors = diagnostics.count { it.file != null && it.test == null },
                testFailures = diagnostics.count { it.test != null },
            )
    }
}

/** What makes two diagnostics the same error. The line doesn't: an edit above an error moves it. */
internal data class ErrorKey(
    val tool: String,
    val file: String?,
    val test: String?,
    val message: String,
)

internal val Diagnostic.key get() = ErrorKey(tool, file, test, message)

/**
 * Whether an attempt that didn't lower the counts only uncovered compile errors that its [sent] errors hid (concept
 * section 6.4): javac reports one error per call chain, so a correct fix can bring up the next one. It did if every
 * error it was sent is a compile error and gone, no new error lies in its [changedLines] (per file), no new build error
 * without a location came up, no count rose, and no error that an earlier accepted attempt removed ([removedEarlier]) is
 * back. Test failures never qualify: one fixed and another broken is no progress. [sent] is never empty.
 */
internal fun uncovers(
    sent: List<Diagnostic>,
    before: List<Diagnostic>,
    after: List<Diagnostic>,
    changedLines: Map<String, Set<Int>>,
    removedEarlier: Set<ErrorKey>,
): Boolean {
    val beforeKeys = before.map { it.key }.toSet()
    val afterKeys = after.map { it.key }.toSet()
    val new = after.filter { it.key !in beforeKeys }
    val countsBefore = Progress.of(before)
    val countsAfter = Progress.of(after)
    return sent.all { isCompileError(it) && it.key !in afterKeys } &&
        new.none { it.line != null && it.line in changedLines[it.file].orEmpty() } &&
        new.none { it.file == null && it.test == null } &&
        countsAfter.buildErrors <= countsBefore.buildErrors &&
        countsAfter.compileErrors <= countsBefore.compileErrors &&
        countsAfter.testFailures <= countsBefore.testFailures &&
        afterKeys.none { it in removedEarlier }
}

private fun isCompileError(diagnostic: Diagnostic) = diagnostic.file != null && diagnostic.test == null
