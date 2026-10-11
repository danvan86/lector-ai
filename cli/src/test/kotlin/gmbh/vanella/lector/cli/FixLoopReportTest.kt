package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.Attempt
import gmbh.vanella.lector.core.AttemptOutcome
import gmbh.vanella.lector.core.FixLoopResult
import gmbh.vanella.lector.core.StopReason
import gmbh.vanella.lector.core.Verification
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FixLoopReportTest {
    @Test
    fun `every stop reason has a sentence and a JSON name`() {
        val expected =
            mapOf(
                StopReason.NOTHING_LEFT to ("nothing caused by the update is left" to "nothingLeft"),
                StopReason.ATTEMPT_BUDGET to ("the attempt budget is used up" to "attemptBudget"),
                StopReason.TIME_BUDGET to ("the time budget is used up" to "timeBudget"),
                StopReason.NO_PROGRESS to ("no progress in the last attempts" to "noProgress"),
                StopReason.CLUSTER_BUDGETS to ("every cluster used its attempts" to "clusterBudgets"),
            )
        expected.forEach { (reason, texts) ->
            val result = FixLoopResult(emptyList(), reason, Verification())

            assertEquals(
                "Fix loop (0 attempts, 0 accepted; stopped: ${texts.first})\n  Attempts (0):\n    none\n  Fixes (0):\n    none",
                FixLoopReport.text(result),
            )
            assertEquals(
                texts.second,
                FixLoopReport
                    .json(result)
                    .getValue("stopReason")
                    .jsonPrimitive.content,
            )
        }
    }

    @Test
    fun `a rejection reason over several lines stays indented under its attempt`() {
        val attempt =
            Attempt("pom.xml", emptyList(), emptyList(), AttemptOutcome.Rejected("the resolved versions can't be read:\nMaven failed"))

        val text = FixLoopReport.text(FixLoopResult(listOf(attempt), StopReason.NO_PROGRESS, Verification()))

        assertContains(text, "    1. pom.xml, 0 diagnostics: rejected: the resolved versions can't be read:\n       Maven failed")
    }
}
