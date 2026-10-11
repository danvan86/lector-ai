package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.Attempt
import gmbh.vanella.lector.core.AttemptOutcome
import gmbh.vanella.lector.core.FixLoopResult
import gmbh.vanella.lector.core.StopReason
import gmbh.vanella.lector.core.Verification
import gmbh.vanella.lector.core.Violation
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
                "Fix loop (0 attempts, 0 accepted; stopped: ${texts.first})\n  Attempts (0):\n    none\n  Fixes (0):\n    none\n" +
                    "  Rejections per guardrail (0):\n    none\n  Tests run in the final verify: unknown",
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
    fun `guardrail rejections are listed with each attempt and counted per rule, with the tests run`() {
        val stub = Violation("stubbing", "App.java", 3, "the edits add the placeholder `return null;` in place of logic")
        val deleted = Violation("test-deletion", "AppTest.java", 9, "the edits delete the test `AppTest.total`")
        val attempts =
            listOf(
                rejected(stub, stub.copy(line = 7), deleted),
                rejected(stub),
                Attempt("symbol:broken", emptyList(), emptyList(), AttemptOutcome.Rejected("no progress")),
            )

        val result = FixLoopResult(attempts, StopReason.NO_PROGRESS, Verification(testsRun = 12))
        val text = FixLoopReport.text(result)

        assertContains(
            text,
            "    1. symbol:broken, 0 diagnostics: rejected: the edits break the guardrails:\n" +
                "       - [stubbing] App.java:3: the edits add the placeholder `return null;` in place of logic\n" +
                "       - [stubbing] App.java:7: the edits add the placeholder `return null;` in place of logic\n" +
                "       - [test-deletion] AppTest.java:9: the edits delete the test `AppTest.total`\n",
        )
        assertContains(
            text,
            "  Rejections per guardrail (2):\n    - stubbing: 2\n    - test-deletion: 1\n  Tests run in the final verify: 12",
        )
        val json = FixLoopReport.json(result)
        assertEquals(
            JsonObject(mapOf("stubbing" to JsonPrimitive(2), "test-deletion" to JsonPrimitive(1))),
            json.getValue("rejectionsPerGuardrail"),
        )
        assertEquals(JsonPrimitive(12), json.getValue("testsRun"))
        val violations =
            json
                .getValue("attempts")
                .jsonArray[0]
                .jsonObject
                .getValue("violations")
                .jsonArray
        assertEquals(
            JsonObject(
                mapOf(
                    "rule" to JsonPrimitive("stubbing"),
                    "file" to JsonPrimitive("App.java"),
                    "line" to JsonPrimitive(3),
                    "reason" to JsonPrimitive(stub.reason),
                ),
            ),
            violations[0],
        )
        assertEquals(3, violations.size)
    }

    @Test
    fun `unknown tests run are null in JSON, and a violation of the whole attempt has no file or line`() {
        val result = FixLoopResult(listOf(rejected(Violation("diff-size", null, null, "too big"))), StopReason.NO_PROGRESS, Verification())

        val json = FixLoopReport.json(result)

        assertEquals(JsonNull, json.getValue("testsRun"))
        val violation =
            json
                .getValue("attempts")
                .jsonArray[0]
                .jsonObject
                .getValue("violations")
                .jsonArray[0]
                .jsonObject
        assertEquals(JsonNull, violation.getValue("file"))
        assertEquals(JsonNull, violation.getValue("line"))
    }

    @Test
    fun `a rejection reason over several lines stays indented under its attempt`() {
        val attempt =
            Attempt("pom.xml", emptyList(), emptyList(), AttemptOutcome.Rejected("the resolved versions can't be read:\nMaven failed"))

        val text = FixLoopReport.text(FixLoopResult(listOf(attempt), StopReason.NO_PROGRESS, Verification()))

        assertContains(text, "    1. pom.xml, 0 diagnostics: rejected: the resolved versions can't be read:\n       Maven failed")
    }

    private fun rejected(vararg violations: Violation) =
        Attempt(
            "symbol:broken",
            emptyList(),
            emptyList(),
            AttemptOutcome.Rejected("the edits break the guardrails:\n" + violations.joinToString("\n") { "- $it" }, violations.toList()),
        )
}
