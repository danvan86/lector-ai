package gmbh.vanella.lector.core

/**
 * The attributed result of a verify run. Only [causedByUpdate] is the tool's job. The other lists are reported so that
 * reviewers see them, but they never fail the job.
 */
data class Verification(
    val causedByUpdate: List<Diagnostic> = emptyList(),
    val preExisting: List<Diagnostic> = emptyList(),
    val flaky: List<Diagnostic> = emptyList(),
    val unattributed: List<Diagnostic> = emptyList(),
)
