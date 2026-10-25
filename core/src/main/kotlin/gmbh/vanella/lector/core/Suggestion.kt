package gmbh.vanella.lector.core

/**
 * The rejected attempt that got furthest on the run's last commit, as the unified [diff] of its edits: never applied or
 * committed, shown to the reviewer to finish by hand (concept section 8.3). [attempt] is its number in the run, from 1.
 */
data class Suggestion(
    val attempt: Int,
    val diff: String,
)

/**
 * The best rejected attempt on the run's current commit so far: built and guardrail-clean, with the lowest counts after
 * it, compared stage by stage; of a tie, the later one. Counts on different commits aren't comparable, and an attempt on
 * an older commit may not apply to the pushed one, so a new commit [clear]s it.
 */
internal class BestRejected {
    private var counts: Progress? = null

    var suggestion: Suggestion? = null
        private set

    /** Offers built attempt [number] with [counts] after it; [diff] gives its edits as a unified diff, if it is the best. */
    fun offer(
        number: Int,
        counts: Progress,
        diff: () -> String,
    ) {
        val best = this.counts
        if (best == null || counts <= best) {
            this.counts = counts
            suggestion = Suggestion(number, diff())
        }
    }

    fun clear() {
        counts = null
        suggestion = null
    }
}
