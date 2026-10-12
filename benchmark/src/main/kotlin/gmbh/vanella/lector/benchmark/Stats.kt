package gmbh.vanella.lector.benchmark

import kotlin.math.roundToInt

/** The recipe's mean share of changed lines at which the 90/10 hypothesis holds (spec section 5, Q11). */
const val RECIPE_SHARE_THRESHOLD = 90.0

/** A mean and its spread, the lowest and the highest value it was taken over. */
data class Spread(
    val mean: Double,
    val min: Double,
    val max: Double,
) {
    companion object {
        fun of(values: List<Double>) = Spread(values.average(), values.min(), values.max())
    }
}

/**
 * The 90/10 verdict: the recipe's mean share of the changed lines, in percent with one decimal, over the [runs] green
 * recipe + fix-loop runs that changed anything. The hypothesis holds at [RECIPE_SHARE_THRESHOLD] or more.
 */
data class Verdict(
    val recipeShare: Double,
    val runs: Int,
) {
    val holds get() = recipeShare >= RECIPE_SHARE_THRESHOLD
}

/**
 * The runs that count: valid runs of valid cases. A case is invalid if any of its runs found no failure caused by the
 * update: it measures nothing (spec section 1).
 */
fun counted(results: Results): List<Pair<RunKey, Metrics>> {
    val invalid = invalidCases(results)
    return results.runs.filter { it.case !in invalid }.mapNotNull { run -> run.metrics?.let { run.key to it } }
}

/** The cases that some run found without a failure caused by the update, in corpus order. */
fun invalidCases(results: Results): List<String> =
    results.runs
        .filter { it.status == RunStatus.INVALID }
        .map { it.case }
        .distinct()

/** The green rate of [mode] in percent: per repetition over the cases, then mean and spread over the repetitions. */
fun greenRate(
    runs: List<Pair<RunKey, Metrics>>,
    mode: Mode,
): Spread? {
    val rates =
        runs
            .filter { it.first.mode == mode }
            .groupBy { it.first.repetition }
            .toSortedMap()
            .values
            .map { repetition -> percent(repetition.count { it.second.green }, repetition.size) }
    return if (rates.isEmpty()) null else Spread.of(rates)
}

/** The 90/10 verdict over the green recipe + fix-loop runs, or null if none changed anything. */
fun verdict(runs: List<Pair<RunKey, Metrics>>): Verdict? {
    val shares =
        runs
            .filter { (key, metrics) -> key.mode == Mode.RECIPE_AND_FIX_LOOP && metrics.green }
            .map { it.second }
            .filter { it.recipeLines + it.fixLines > 0 }
            .map { percent(it.recipeLines, it.recipeLines + it.fixLines) }
    // Rounded to what the report shows, so the verdict and the number never disagree.
    return if (shares.isEmpty()) null else Verdict(oneDecimal(shares.average()), shares.size)
}

private const val PERCENT = 100.0
private const val TENTHS = 10.0

fun percent(
    part: Int,
    whole: Int,
) = PERCENT * part / whole

fun oneDecimal(value: Double) = (value * TENTHS).roundToInt() / TENTHS
