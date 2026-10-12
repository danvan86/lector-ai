package gmbh.vanella.lector.core

/**
 * The port to the language model. It turns one grounded request into search/replace edits (rule 3). Unreachable or
 * misconfigured, it fails with a [ToolError]. A reply that isn't the expected structure is [Proposal.Invalid]: a failed
 * attempt, not a tool error.
 */
fun interface LlmClient {
    fun propose(request: FixRequest): Proposal
}

/**
 * Everything the model gets for one attempt on one cluster: the [changes], the [diagnostics], the code around them, the
 * API surface of the new versions ([grounding]), the [releaseNotes] and the [recipeDiff] if there are any, and on a retry the
 * [previousAttempts] with why they failed. The client decides how it fits into the model's context.
 */
data class FixRequest(
    val changes: List<DependencyChange>,
    val diagnostics: List<Diagnostic>,
    val snippets: List<Snippet>,
    val grounding: String,
    val releaseNotes: String?,
    val recipeDiff: String?,
    val previousAttempts: List<PreviousAttempt>,
    val temperature: Double,
)

/** An earlier, rejected attempt on the same cluster: what the model proposed, and why it was rejected. */
data class PreviousAttempt(
    val edits: List<Edit>,
    val failure: String,
)

sealed interface Proposal {
    /** The edits and a one-line rationale, which becomes the commit subject. */
    data class Edits(
        val edits: List<Edit>,
        val rationale: String,
    ) : Proposal

    /** The reply didn't match the expected structure. */
    data class Invalid(
        val reason: String,
    ) : Proposal
}
