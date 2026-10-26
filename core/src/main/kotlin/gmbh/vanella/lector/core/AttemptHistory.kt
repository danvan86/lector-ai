package gmbh.vanella.lector.core

/** The attempts of one fix loop, in order, and what they tell its next request (concept section 6.4). */
internal class AttemptHistory {
    private val attempts = mutableListOf<Attempt>()

    val all get() = attempts.toList()

    val size get() = attempts.size

    fun add(attempt: Attempt) {
        attempts += attempt
    }

    /** The attempts on [cluster]. */
    fun on(cluster: String) = attempts.filter { it.cluster == cluster }

    /** Every failed attempt on [cluster], of every kind, with its edits and why it failed: never trimmed. */
    fun failedOn(cluster: String) =
        on(cluster).mapNotNull { attempt ->
            (attempt.outcome as? AttemptOutcome.Rejected)?.let { PreviousAttempt(attempt.edits, it.reason) }
        }

    /**
     * The number (from 1) of the rejected attempt on [cluster] since the last accepted one that [proposal] repeats: the
     * same edits in any order, whitespace at line ends ignored. Null if none, or if it proposes no edits. After an
     * accepted attempt, the same edits are on another commit and may succeed.
     */
    fun repeatOf(
        cluster: String,
        proposal: Proposal,
    ): Int? {
        val answer = sameness(edits(proposal))
        if (answer.isEmpty()) return null
        val since = attempts.indexOfLast { it.outcome is AttemptOutcome.Accepted } + 1
        val index = (since until attempts.size).lastOrNull { attempts[it].cluster == cluster && sameness(attempts[it].edits) == answer }
        return index?.plus(1)
    }

    /** What the model reads with its second answer when its first repeated attempt [number]: those edits, and why they failed. */
    fun note(number: Int): PreviousAttempt {
        val repeated = attempts[number - 1]
        val reason = (repeated.outcome as AttemptOutcome.Rejected).reason
        return PreviousAttempt(
            repeated.edits,
            "your answer repeated attempt $number exactly, which was rejected: $reason\nPropose different edits.",
        )
    }
}

/** The edits of [proposal], none if it has none. */
internal fun edits(proposal: Proposal) =
    when (proposal) {
        is Proposal.Edits -> proposal.edits
        is Proposal.Invalid, is Proposal.Unanswered -> emptyList()
    }

/** [edits] as a repeat compares them: in any order, without whitespace at line ends. */
private fun sameness(edits: List<Edit>) =
    edits
        .map { edit -> listOf(edit.file, edit.search, edit.replace).map { text -> text.lines().joinToString("\n") { it.trimEnd() } } }
        .groupingBy { it }
        .eachCount()
