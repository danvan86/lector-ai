package gmbh.vanella.lector.benchmark

import gmbh.vanella.lector.core.SHORT_COMMIT
import java.util.Locale

private val MODE_NAMES =
    mapOf(Mode.RECIPE_ONLY to "Recipe only", Mode.FIX_LOOP_ONLY to "Fix loop only", Mode.RECIPE_AND_FIX_LOOP to "Recipe + fix loop")
private const val SECONDS_PER_MINUTE = 60.0

// The manual review's sample: at least this many green fix-loop runs, or all of them if there are fewer (spec section 4).
private const val REVIEW_SAMPLE = 10

// The harness passes no release notes to the fix loop (review F4): Dependabot PRs have none in the product before M8.
private const val NO_RELEASE_NOTES =
    "The fix requests carry no release notes. `lector pr` passes the notes of a Renovate PR's body, so for those cases " +
        "the fix-loop numbers are a lower bound."

// How the report names the ecosystems whose numbers it shows apart (concept section 11, M9).
private val ECOSYSTEM_NAMES = mapOf(MAVEN to "JVM", "npm" to "TypeScript")

/**
 * `report.md`: the numbers of one benchmark run (spec section 5), from its results and the manual [review], if there is
 * one. With cases of more than one ecosystem, each ecosystem gets its own numbers, JVM first.
 */
object Report {
    fun render(
        results: Results,
        review: Review?,
    ): String {
        val ecosystems =
            results.runs
                .map { it.ecosystem }
                .distinct()
                .sortedWith(compareBy({ it != MAVEN }, { it }))
        val numbers =
            ecosystems.flatMap { ecosystem ->
                val part = results.copy(runs = results.runs.filter { it.ecosystem == ecosystem })
                val heading = "# ${ECOSYSTEM_NAMES[ecosystem] ?: ecosystem} (`$ecosystem`)"
                (if (ecosystems.size > 1) listOf(heading) else emptyList()) + numbers(part, ecosystem)
            }
        val sections = listOf(header(results)) + numbers + listOf(reviewSection(results, review), problems(results))
        return sections.joinToString("\n\n") + "\n"
    }

    /**
     * The numbers of [results], all of [ecosystem]: the verdict, the green rates, the cases, the fix loop, the rejections,
     * the runtime. An ecosystem without recipes has no verdict and no recipe modes.
     */
    private fun numbers(
        results: Results,
        ecosystem: String,
    ): List<String> {
        val runs = counted(results)
        val modes = modes(ecosystem)
        val verdict =
            if (Mode.RECIPE_ONLY in modes) {
                verdictSection(runs)
            } else {
                "## Recipe step\n\nNone for `$ecosystem`: its ecosystem has no recipes, so only the fix loop runs."
            }
        return listOf(verdict, greenRates(runs, modes), cases(results, modes), fixLoop(runs, modes), rejections(runs), runtime(runs, modes))
    }

    private fun header(results: Results): String {
        val cases = results.runs.map { it.case }.distinct()
        val invalid = invalidCases(results)
        val invalidNote = if (invalid.isEmpty()) "" else ": ${invalid.joinToString(", ")}, excluded from the numbers"
        return """
            |# Benchmark ${results.date}
            |
            |Tool commit `${results.toolCommit.take(SHORT_COMMIT)}` · model `${results.model}` · runner ${results.runner} · ${results.date}
            |
            |${cases.size} cases, ${invalid.size} invalid$invalidNote.
            |
            |$NO_RELEASE_NOTES
            """.trimMargin()
    }

    private fun verdictSection(runs: List<Pair<RunKey, Metrics>>): String {
        val verdict = verdict(runs)
        val text =
            if (verdict == null) {
                "No verdict: no recipe + fix-loop run ended green with changes."
            } else {
                val holds = if (verdict.holds) "holds" else "doesn't hold"
                "The hypothesis **$holds**: on ${verdict.runs} green recipe + fix-loop runs, the recipe commits changed " +
                    "${format(verdict.recipeShare)}% of the lines on average, the fix commits the rest " +
                    "(holds at ${format(RECIPE_SHARE_THRESHOLD)}% or more)."
            }
        return "## 90/10 verdict\n\n$text"
    }

    private fun greenRates(
        runs: List<Pair<RunKey, Metrics>>,
        modes: List<Mode>,
    ): String {
        val rows =
            modes.map { mode ->
                val metrics = runs.filter { it.first.mode == mode }.map { it.second }
                val rate = greenRate(runs, mode)
                val rateCells = if (rate == null) "– | –" else "${format(rate.mean)}% | ${format(rate.min)}–${format(rate.max)}%"
                "| ${MODE_NAMES.getValue(mode)} | ${metrics.size} | $rateCells | " +
                    "${metrics.sumOf { it.preExisting }} / ${metrics.sumOf { it.flaky }} / ${metrics.sumOf { it.unattributed }} |"
            }
        return table(
            "## Green rate per mode",
            "Green: no failure caused by the update is left, and tests ran. Per repetition over the valid cases, then the mean " +
                "and the spread over the repetitions. Pre-existing, flaky, and unattributed failures after the runs are " +
                "tracked, but never the tool's job.",
            listOf("Mode", "Runs", "Green rate", "Spread", "Pre-existing / flaky / unattributed"),
            rows,
        )
    }

    private fun cases(
        results: Results,
        modes: List<Mode>,
    ): String {
        val invalid = invalidCases(results)
        val byCase = results.runs.groupBy { it.case }

        // Per case and mode, whether each run with a result ended green.
        fun green(
            case: String,
            mode: Mode,
        ) = byCase.getValue(case).filter { it.mode == mode }.mapNotNull { run -> run.metrics?.green }
        val rows =
            byCase.keys.map { case ->
                val cells =
                    modes.map { mode ->
                        val runs = green(case, mode)
                        when {
                            case in invalid -> "invalid"
                            runs.isEmpty() -> "–"
                            else -> "${runs.count { it }}/${runs.size}"
                        }
                    }
                "| $case | ${cells.joinToString(" | ")} |"
            }
        val valid = byCase.keys - invalid.toSet()
        val recipeAlone = valid.count { case -> green(case, Mode.RECIPE_ONLY).any { it } }
        // The full flow, or the fix loop where there are no recipes.
        val healing = modes.last()
        val healedOnce = valid.count { case -> green(case, healing).any { it } }
        val healedAlways = valid.count { case -> green(case, healing).let { it.isNotEmpty() && it.all { green -> green } } }
        val healed = "$healedOnce in at least one run, $healedAlways in every run."
        val summary =
            "Each cell: green runs of the runs with a result. " +
                if (healing == Mode.RECIPE_AND_FIX_LOOP) {
                    "Green after the recipe alone: $recipeAlone of ${valid.size} valid cases. Green after recipe + fix loop: $healed"
                } else {
                    "Green after the fix loop: $healed"
                }
        return table("## Cases", summary, listOf("Case") + modes.map { MODE_NAMES.getValue(it) }, rows)
    }

    private fun fixLoop(
        runs: List<Pair<RunKey, Metrics>>,
        modes: List<Mode>,
    ): String {
        val rows =
            modes.filter { it.usesFixLoop }.map { mode ->
                val metrics = runs.filter { it.first.mode == mode }.map { it.second }
                val stops =
                    metrics
                        .groupingBy { it.stopReason.toString() }
                        .eachCount()
                        .toSortedMap()
                        .entries
                        .joinToString(", ") { "${it.key} ${it.value}" }
                "| ${MODE_NAMES.getValue(mode)} | ${mean(metrics) { it.attempts }} | ${mean(metrics) { it.acceptedFixes }} | " +
                    "${metrics.sumOf { it.requestsWithDiagnosticsLeftOut }} | $stops |"
            }
        return table(
            "## Fix loop",
            "Attempts and accepted fixes are means per run. Cut requests: fix requests that left diagnostics out because " +
                "they didn't fit into the model's context.",
            listOf("Mode", "Attempts", "Accepted fixes", "Cut requests", "Stop reasons"),
            rows,
        )
    }

    private fun rejections(runs: List<Pair<RunKey, Metrics>>): String {
        val loops = runs.filter { it.first.mode.usesFixLoop }.map { it.second }
        val attempts = loops.sumOf { it.attempts }
        val perRule =
            loops
                .flatMap { it.rejectionsPerRule.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { it.value.sum() }
                .toSortedMap()
        val rows = perRule.map { (rule, count) -> "| $rule | $count | ${format(percent(count, attempts))}% |" }
        return table(
            "## Guardrail rejections",
            "Over the $attempts attempts of both fix-loop modes. An attempt can break more than one rule.",
            listOf("Rule", "Rejected attempts", "Rate"),
            rows.ifEmpty { listOf("| none | 0 | – |") },
        )
    }

    private fun runtime(
        runs: List<Pair<RunKey, Metrics>>,
        modes: List<Mode>,
    ): String {
        val rows =
            modes.map { mode ->
                val times = runs.filter { it.first.mode == mode }.map { it.second.runtime }
                "| ${MODE_NAMES.getValue(mode)} | ${minutes(times) { it.total }} | ${minutes(times) { it.verify }} | " +
                    "${minutes(times) { it.recipe }} | ${minutes(times) { it.fixLoop }} |"
            }
        return table("## Runtime", "Mean minutes per run.", listOf("Mode", "Total", "First verify", "Recipe", "Fix loop"), rows)
    }

    private fun reviewSection(
        results: Results,
        review: Review?,
    ): String {
        if (review == null) return "## Manual review\n\nNot done yet: add `review.json` and generate the report again."
        val runs = results.runs.associateBy { it.key }
        review.runs.forEach { reviewed ->
            val run = runs[reviewed.key]
            require(run != null && reviewed.mode.usesFixLoop && run.metrics?.green == true) {
                "The review covers ${reviewed.key}, which is not a green run of a fix-loop mode"
            }
        }
        val twice =
            review.runs
                .groupBy { it.key }
                .filterValues { it.size > 1 }
                .keys
        require(twice.isEmpty()) { "The review covers ${twice.joinToString(", ")} more than once" }
        val greenRuns = results.runs.count { it.mode.usesFixLoop && it.metrics?.green == true }
        val needed = minOf(REVIEW_SAMPLE, greenRuns)
        require(review.runs.size >= needed) {
            "The review covers ${review.runs.size} runs, not the $needed it needs: at least $REVIEW_SAMPLE green runs of the " +
                "fix-loop modes, or all if there are fewer"
        }
        val accepted = review.runs.count { it.acceptedUnchanged }
        val rows =
            review.runs.map {
                "| ${it.case} | ${MODE_NAMES.getValue(
                    it.mode,
                )} | ${it.repetition} | ${if (it.acceptedUnchanged) "yes" else "no"} | ${it.reason} |"
            }
        return table(
            "## Manual review",
            "$accepted of ${review.runs.size} reviewed green runs (of $greenRuns in the fix-loop modes) would be accepted " +
                "unchanged (${format(percent(accepted, review.runs.size))}%). Reviewer: ${review.reviewer}.",
            listOf("Case", "Mode", "Repetition", "Accepted unchanged", "Reason"),
            rows,
        )
    }

    private fun problems(results: Results): String {
        val failed = results.runs.filter { it.status == RunStatus.ERROR || it.status == RunStatus.MISSING }
        if (failed.isEmpty()) return "## Errors and missing runs\n\nNone."
        val rows =
            failed.map {
                "| ${it.case} | ${MODE_NAMES.getValue(
                    it.mode,
                )} | ${it.repetition} | ${it.status.name.lowercase()} | ${it.reason} |"
            }
        return table(
            "## Errors and missing runs",
            "Not counted in the numbers.",
            listOf("Case", "Mode", "Repetition", "Status", "Reason"),
            rows,
        )
    }
}

private fun table(
    title: String,
    intro: String,
    columns: List<String>,
    rows: List<String>,
) = "$title\n\n$intro\n\n| ${columns.joinToString(" | ")} |\n|${columns.joinToString("|") { "---" }}|\n" + rows.joinToString("\n")

private fun mean(
    metrics: List<Metrics>,
    value: (Metrics) -> Int,
) = if (metrics.isEmpty()) "–" else format(metrics.map(value).average())

private fun minutes(
    times: List<Runtime>,
    value: (Runtime) -> Long,
) = if (times.isEmpty()) "–" else format(times.map(value).average() / SECONDS_PER_MINUTE)

private fun format(value: Double) = String.format(Locale.ROOT, "%.1f", value)
