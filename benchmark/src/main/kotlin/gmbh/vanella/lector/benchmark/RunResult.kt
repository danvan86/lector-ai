package gmbh.vanella.lector.benchmark

import gmbh.vanella.lector.core.Progress
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The result of one run. Every run records the tool commit, the model, and its case's [ecosystem]. Only
 * [RunStatus.VALID] runs have [metrics] and count in the numbers; the others say in [reason] why not.
 */
@Serializable
data class RunResult(
    val case: String,
    val mode: Mode,
    val repetition: Int,
    val toolCommit: String,
    val model: String,
    val status: RunStatus,
    val reason: String?,
    val metrics: Metrics?,
    val ecosystem: String,
) {
    val key get() = RunKey(case, mode, repetition)
}

@Serializable
enum class RunStatus {
    /** The case had a failure caused by the update, and the run finished. */
    @SerialName("valid")
    VALID,

    /** The head commit has no failure caused by the update: the case measures nothing (spec section 1). */
    @SerialName("invalid")
    INVALID,

    /** The run failed: the checkout, a build that can't start, Ollama out of reach. */
    @SerialName("error")
    ERROR,

    /** The run left no result, e.g. its job ran out of time. Added when the results are merged. */
    @SerialName("missing")
    MISSING,
}

/** What one valid run measured (spec section 3). Runtimes are in whole seconds. */
@Serializable
data class Metrics(
    /** No failure caused by the update is left, and tests ran. */
    val green: Boolean,
    val before: Stages,
    val after: Stages,
    val recipes: List<RecipeRecord>,
    val attempts: Int,
    val acceptedFixes: Int,
    val rejectionsPerRule: Map<String, Int>,
    /** Why the fix loop stopped, null if the mode has none. */
    val stopReason: String?,
    val runtime: Runtime,
    val recipeLines: Int,
    val fixLines: Int,
    val preExisting: Int,
    val flaky: Int,
    val unattributed: Int,
    val testsRun: Int?,
    val requestsWithDiagnosticsLeftOut: Int,
)

/** The failures caused by the update, per stage, as the fix loop counts them. */
@Serializable
data class Stages(
    val buildErrors: Int,
    val compileErrors: Int,
    val testFailures: Int,
) {
    companion object {
        fun of(progress: Progress) = Stages(progress.buildErrors, progress.compileErrors, progress.testFailures)
    }
}

/** For one change of the case: the recipe found for it, if any, and whether its result was kept, or why not. */
@Serializable
data class RecipeRecord(
    val change: String,
    val recipe: String?,
    val applied: Boolean,
    val outcome: String,
)

/** Seconds in total, for the first verification, for the recipe and the verification after it, and for the fix loop. */
@Serializable
data class Runtime(
    val total: Long,
    val verify: Long,
    val recipe: Long,
    val fixLoop: Long,
)
