package gmbh.vanella.lector.benchmark

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The model's output varies even at a low temperature: three runs tell signal from noise (spec section 2, Q4).
private const val MODEL_REPETITIONS = 3

/** What a run of a case does. Only the three together tell where the value comes from (Q3). */
@Serializable
enum class Mode(
    val id: String,
    val usesRecipe: Boolean,
    val usesFixLoop: Boolean,
) {
    /** Recipe, then verify. Deterministic. */
    @SerialName("recipe-only")
    RECIPE_ONLY("recipe-only", usesRecipe = true, usesFixLoop = false),

    /** Verify, then the fix loop, with no recipe. */
    @SerialName("fix-loop-only")
    FIX_LOOP_ONLY("fix-loop-only", usesRecipe = false, usesFixLoop = true),

    /** The full `heal` flow: recipe, verify, fix loop. */
    @SerialName("recipe-and-fix-loop")
    RECIPE_AND_FIX_LOOP("recipe-and-fix-loop", usesRecipe = true, usesFixLoop = true),
    ;

    /** Runs per case: three for the modes that use the model, one for the recipe alone. */
    val repetitions get() = if (usesFixLoop) MODEL_REPETITIONS else 1
}

/** One run of the benchmark: a case, a mode, and which repetition (from 1). */
data class RunKey(
    val case: String,
    val mode: Mode,
    val repetition: Int,
)

/**
 * Every run of [corpus], or of the cases in [only] if it isn't null: each case in each mode, as often as the mode runs.
 * A case of an ecosystem without recipes runs only the fix loop: the recipe modes would repeat it.
 */
fun plan(
    corpus: Corpus,
    only: Set<String>? = null,
): List<RunKey> {
    val cases = only?.map { corpus.case(it) } ?: corpus.cases
    return cases.flatMap { case -> modes(case.ecosystem).flatMap { mode -> (1..mode.repetitions).map { RunKey(case.id, mode, it) } } }
}

/** The modes that run for a case of [ecosystem]. */
fun modes(ecosystem: String) = if (ecosystem in RECIPE_ECOSYSTEMS) Mode.entries else listOf(Mode.FIX_LOOP_ONLY)
