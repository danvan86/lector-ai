package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.FixLoopResult
import gmbh.vanella.lector.core.StopReason
import gmbh.vanella.lector.core.Suggestion
import gmbh.vanella.lector.core.Verification
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** M11 section 7: `heal`'s text and JSON reports carry the suggestion of a red run, and nothing when there is none. */
class FixLoopReportSuggestionTest {
    private val diff = "--- a/App.java\n+++ b/App.java\n@@ -3 +3 @@\n-        missing();\n+        missing(); // b"

    @Test
    fun `the text report shows the suggestion, marked not applied, with its diff`() {
        val text = FixLoopReport.text(result(Suggestion(attempt = 6, diff = diff)))

        assertContains(text, "not applied, needs review")
        assertContains(text, "attempt 6")
        diff.lines().forEach { assertContains(text, it) }
    }

    @Test
    fun `the JSON report has the suggestion's attempt and diff, or null`() {
        assertEquals(
            JsonObject(mapOf("attempt" to JsonPrimitive(6), "diff" to JsonPrimitive(diff))),
            FixLoopReport.json(result(Suggestion(attempt = 6, diff = diff)))["suggestion"],
        )
        assertEquals(JsonNull, FixLoopReport.json(result(suggestion = null))["suggestion"])
    }

    @Test
    fun `without a suggestion the text report has no suggestion section`() {
        assertFalse("not applied, needs review" in FixLoopReport.text(result(suggestion = null)))
    }

    private fun result(suggestion: Suggestion?) =
        FixLoopResult(emptyList(), StopReason.NO_PROGRESS, Verification(), suggestion = suggestion)
}
