package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.Attempt
import gmbh.vanella.lector.core.AttemptOutcome
import gmbh.vanella.lector.core.FixLoopResult
import gmbh.vanella.lector.core.StopReason
import gmbh.vanella.lector.core.Violation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Enough to find a commit in a small history, like `git log --oneline`.
internal const val SHORT_COMMIT = 7

/**
 * The fix-loop section of the `heal` report: every attempt, every fix with its commit, the guardrail rejections per
 * rule, and the tests the final verify ran, so reviewers see what the model tried.
 */
internal object FixLoopReport {
    fun text(result: FixLoopResult): String {
        val fixes = result.attempts.map { it.outcome }.filterIsInstance<AttemptOutcome.Accepted>()
        val attempts = result.attempts.mapIndexed(::line)
        val lines =
            listOf("Fix loop (${result.attempts.size} attempts, ${fixes.size} accepted; stopped: ${stopReason(result.stopReason)})") +
                list("Attempts", attempts) +
                list("Fixes", fixes.map { "- ${it.commit.take(SHORT_COMMIT)} ${it.rationale}" }) +
                list("Rejections per guardrail", rejectionsPerRule(result).map { (rule, count) -> "- $rule: $count" }) +
                "  Tests run in the final verify: ${result.verification.testsRun ?: "unknown"}"
        return lines.joinToString("\n")
    }

    /** How many attempts each guardrail rule rejected, by rule. */
    private fun rejectionsPerRule(result: FixLoopResult) =
        result.attempts
            .map { it.outcome }
            .filterIsInstance<AttemptOutcome.Rejected>()
            .flatMap { rejected -> rejected.violations.map { it.rule }.distinct() }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()

    private fun line(
        index: Int,
        attempt: Attempt,
    ) = "${index + 1}. ${attempt.cluster}, ${attempt.diagnostics.size} diagnostics: ${outcome(attempt.outcome)}"

    fun json(result: FixLoopResult) =
        JsonObject(
            mapOf(
                "stopReason" to JsonPrimitive(name(result.stopReason)),
                "attempts" to JsonArray(result.attempts.map(::json)),
                "rejectionsPerGuardrail" to JsonObject(rejectionsPerRule(result).mapValues { JsonPrimitive(it.value) }),
                "testsRun" to JsonPrimitive(result.verification.testsRun),
            ),
        )

    private fun json(attempt: Attempt): JsonObject {
        val accepted = attempt.outcome as? AttemptOutcome.Accepted
        return JsonObject(
            mapOf(
                "cluster" to JsonPrimitive(attempt.cluster),
                "diagnostics" to JsonReport.array(attempt.diagnostics),
                "edits" to
                    JsonArray(
                        attempt.edits.map {
                            JsonObject(
                                mapOf(
                                    "file" to JsonPrimitive(it.file),
                                    "search" to JsonPrimitive(it.search),
                                    "replace" to JsonPrimitive(it.replace),
                                ),
                            )
                        },
                    ),
                "outcome" to JsonPrimitive(if (accepted == null) "rejected" else "accepted"),
                "reason" to JsonPrimitive(reason(attempt.outcome)),
                "violations" to JsonArray((attempt.outcome as? AttemptOutcome.Rejected)?.violations.orEmpty().map(::json)),
                "commit" to JsonPrimitive(accepted?.commit),
                "rationale" to JsonPrimitive(accepted?.rationale),
            ),
        )
    }

    private fun json(violation: Violation) =
        JsonObject(
            mapOf(
                "rule" to JsonPrimitive(violation.rule),
                "file" to JsonPrimitive(violation.file),
                "line" to JsonPrimitive(violation.line),
                "reason" to JsonPrimitive(violation.reason),
            ),
        )

    private fun outcome(outcome: AttemptOutcome) =
        when (outcome) {
            is AttemptOutcome.Accepted -> "accepted: ${reason(outcome)}"
            is AttemptOutcome.Rejected -> "rejected: ${reason(outcome).replace("\n", "\n       ")}"
        }

    private fun reason(outcome: AttemptOutcome) =
        when (outcome) {
            is AttemptOutcome.Accepted -> "${outcome.before} -> ${outcome.after}"
            is AttemptOutcome.Rejected -> outcome.reason
        }

    private fun stopReason(reason: StopReason) =
        when (reason) {
            StopReason.NOTHING_LEFT -> "nothing caused by the update is left"
            StopReason.ATTEMPT_BUDGET -> "the attempt budget is used up"
            StopReason.TIME_BUDGET -> "the time budget is used up"
            StopReason.NO_PROGRESS -> "no progress in the last attempts"
            StopReason.CLUSTER_BUDGETS -> "every cluster used its attempts"
        }

    private fun name(reason: StopReason) =
        when (reason) {
            StopReason.NOTHING_LEFT -> "nothingLeft"
            StopReason.ATTEMPT_BUDGET -> "attemptBudget"
            StopReason.TIME_BUDGET -> "timeBudget"
            StopReason.NO_PROGRESS -> "noProgress"
            StopReason.CLUSTER_BUDGETS -> "clusterBudgets"
        }

    private fun list(
        title: String,
        items: List<String>,
    ) = listOf("  $title (${items.size}):") + items.map { "    $it" }.ifEmpty { listOf("    none") }
}
