package gmbh.vanella.lector.benchmark

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val json =
    Json {
        prettyPrint = true
        encodeDefaults = true
    }

/** Every run of one benchmark run, and where it ran: `results.json` (spec section 5). */
@Serializable
data class Results(
    val toolCommit: String,
    val model: String,
    val runner: String,
    val date: String,
    val runs: List<RunResult>,
) {
    fun encode() = json.encodeToString(this) + "\n"

    companion object {
        /**
         * Merges the [runs] of one benchmark run into its results, in the order of [planned]. A planned run without a
         * result is [RunStatus.MISSING]. Fails if the runs come from more than one tool commit or model, or weren't planned.
         */
        fun merge(
            planned: List<RunKey>,
            runs: List<RunResult>,
            runner: String,
            date: String,
        ): Results {
            val toolCommit = runs.map { it.toolCommit }.distinct().singleOrNull()
            val model = runs.map { it.model }.distinct().singleOrNull()
            require(toolCommit != null && model != null) {
                "The runs must share one tool commit and one model, not ${runs.map { it.toolCommit to it.model }.distinct()}"
            }
            val byKey = runs.associateBy { it.key }
            val unplanned = byKey.keys - planned.toSet()
            require(unplanned.isEmpty()) { "Runs that weren't planned: ${unplanned.joinToString(", ")}" }
            val merged =
                planned.map { key ->
                    byKey[key] ?: RunResult(key.case, key.mode, key.repetition, toolCommit, model, RunStatus.MISSING, "no result", null)
                }
            return Results(toolCommit, model, runner, date, merged)
        }

        /** Fails with an [IllegalArgumentException] if [text] isn't valid results. */
        fun decode(text: String): Results = decode(text, "results")

        /** One run's result, as the harness writes it. */
        fun encodeRun(run: RunResult) = json.encodeToString(run) + "\n"

        fun decodeRun(text: String): RunResult = decode(text, "run result")
    }
}

/** The manual review of a sample of green runs from the fix-loop modes (spec section 4): `review.json`. */
@Serializable
data class Review(
    val reviewer: String,
    val runs: List<ReviewedRun>,
) {
    companion object {
        fun decode(text: String): Review = decode(text, "review")
    }
}

/** One reviewed run: whether a reviewer would accept its fixes unchanged, and why, in one line. */
@Serializable
data class ReviewedRun(
    val case: String,
    val mode: Mode,
    val repetition: Int,
    val acceptedUnchanged: Boolean,
    val reason: String,
) {
    val key get() = RunKey(case, mode, repetition)
}

/** Fails with an [IllegalArgumentException] if [text] isn't JSON of [T]. */
private inline fun <reified T> decode(
    text: String,
    what: String,
): T =
    try {
        json.decodeFromString<T>(text)
    } catch (e: SerializationException) {
        throw IllegalArgumentException("Malformed $what: ${e.message}", e)
    }
