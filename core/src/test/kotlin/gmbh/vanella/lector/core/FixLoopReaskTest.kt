package gmbh.vanella.lector.core

import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** How a repeated answer is asked again: the note counts in the fit check, and the second answer is judged as usual. */
class FixLoopReaskTest : FixLoopFixture() {
    private val noProgress = Edit("App.java", "class App {", "class App { // still broken")

    @Test
    fun `the note counts in the fit check of the second request`() {
        repo.resolve("App.java").writeText(app.replace("broken();", "broken();\n        broken(); // twice"))
        git("commit", "--quiet", "-am", "two errors")
        val adapter = FakeAdapter()
        val llm =
            ScriptedLlm(edits(noProgress), edits(noProgress), edits(fix), fits = { it.diagnostics.size + it.previousAttempts.size <= 3 })

        val result = loop(adapter, llm, Budgets(attempts = 2)).run(repo, context, adapter.initial())

        assertEquals(listOf(2, 2, 1), llm.requests.map { it.diagnostics.size }, "the note took the place of a diagnostic")
        assertEquals(1, result.attempts[1].diagnostics.size, "the attempt records what its judged answer was sent")
        assertEquals(1, result.attempts[1].diagnosticsLeftOut)
    }

    @Test
    fun `a second answer that doesn't come stops the loop, and its reason says the first was a repeat`() {
        val adapter = FakeAdapter()
        val llm = ScriptedLlm(edits(noProgress), edits(noProgress), { Proposal.Unanswered("no answer within 10m") })

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        assertEquals(StopReason.MODEL_UNANSWERED, result.stopReason)
        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts[1].outcome)
        assertEquals(
            "the first answer repeated attempt 1, and the second was rejected: the model didn't answer: no answer within 10m",
            rejected.reason,
        )
        assertEquals(emptyList(), result.attempts[1].edits)
    }

    @Test
    fun `a repeat that arrives after the time budget is not asked again, and the loop stops on time`() {
        val adapter = FakeAdapter()
        val slowRepeat: (FixRequest) -> Proposal = { edits(noProgress)(it).also { time += 31.minutes } }
        val llm = ScriptedLlm(edits(noProgress), slowRepeat, edits(fix))

        val result = loop(adapter, llm, Budgets(attemptsWithoutProgress = 3)).run(repo, context, adapter.initial())

        assertEquals(2, llm.requests.size, "no third model call after the budget")
        assertEquals(1, adapter.verifyCalls, "the repeat isn't built")
        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts[1].outcome)
        assertEquals("the first answer repeated attempt 1, and no time was left to ask again", rejected.reason)
        assertEquals(listOf(noProgress), result.attempts[1].edits)
        assertEquals(StopReason.TIME_BUDGET, result.stopReason)
    }

    @Test
    fun `a second answer that repeats another rejected attempt names both`() {
        val other = Edit("App.java", "broken();", "broken(); // other")
        val adapter = FakeAdapter()
        val llm = ScriptedLlm(edits(noProgress), edits(other), edits(noProgress), edits(other))

        val result = loop(adapter, llm, Budgets(attemptsWithoutProgress = 3)).run(repo, context, adapter.initial())

        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts[2].outcome)
        assertEquals("the first answer repeated attempt 1, and the second repeated attempt 2; neither was built", rejected.reason)
        assertTrue(
            llm.requests[3]
                .previousAttempts
                .last()
                .failure
                .startsWith("your answer repeated attempt 1 exactly"),
        )
    }
}
