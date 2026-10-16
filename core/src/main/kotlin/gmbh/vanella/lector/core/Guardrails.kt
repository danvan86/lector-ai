package gmbh.vanella.lector.core

import java.nio.file.Path

/** The most lines (added plus removed) one attempt may change (concept section 7, diff size). */
const val DEFAULT_MAX_CHANGED_LINES = 50

/**
 * The guardrails every attempt of the model passes before it is verified (concept section 7). An internal list, not an
 * extension point: the language knowledge comes from [adapter], and only the diff-size limit is configurable.
 */
class Guardrails(
    private val adapter: EcosystemAdapter,
    maxChangedLines: Int,
) {
    private val rules =
        listOf(
            DiffSize(maxChangedLines),
            BuildFileBlocklist(adapter::isBuildFile, adapter::testDisablingConfig, adapter::loosening),
            ParseErrors,
            SymbolComparison,
            TestDeletion,
            TestSkips,
            TestAssertions,
            TestCount,
            Stubbing,
            Suppression,
        )

    /** The scope violations of edits to [files] (paths as the model wrote them). Checked before the edits are written. */
    fun checkScope(
        repo: Path,
        files: List<String>,
    ) = Scope(repo, GitWorkingTree.trackedFiles(repo).toSet(), adapter::isEditable).check(files)

    /** The violations of the written edits: the [changes] they made, against the [diagnostics] the update caused before them. */
    fun check(
        changes: List<FileChange>,
        diagnostics: List<Diagnostic>,
    ): List<Violation> {
        val attempt = AttemptChanges(changes, diagnostics)
        return rules.flatMap { it.check(attempt) }
    }
}
