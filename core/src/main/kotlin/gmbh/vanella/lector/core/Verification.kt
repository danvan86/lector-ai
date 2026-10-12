package gmbh.vanella.lector.core

/**
 * The attributed result of a verify run. Only [causedByUpdate] is the tool's job. The other lists are reported so that
 * reviewers see them, but they never fail the job. So is [testsRun], the number of tests the run executed, if the
 * adapter can tell (concept section 7: reported, not enforced).
 */
data class Verification(
    val causedByUpdate: List<Diagnostic> = emptyList(),
    val preExisting: List<Diagnostic> = emptyList(),
    val flaky: List<Diagnostic> = emptyList(),
    val unattributed: List<Diagnostic> = emptyList(),
    val testsRun: Int? = null,
)
