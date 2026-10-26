package gmbh.vanella.lector.core

import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * M11 section 4: an answer that repeats a rejected attempt on the same cluster is not built. The model is asked once
 * more, at a higher temperature and with a note; a second repeat rejects the attempt without a build.
 */
class FixLoopRepeatTest : FixLoopFixture() {
    private val noProgress = Edit("App.java", "broken();", "broken(); // still broken")

    @Test
    fun `a repeated answer is not built, and the model is asked once more at a higher temperature with a note`() {
        val adapter = FakeAdapter()
        val llm = ScriptedLlm(edits(noProgress), edits(noProgress), edits(fix))

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        assertEquals(2, result.attempts.size)
        assertIs<AttemptOutcome.Accepted>(result.attempts[1].outcome, "the second answer of attempt 2 fixed it")
        assertEquals(listOf(0.0, 0.3, 0.7), llm.requests.map { it.temperature })
        val note = llm.requests[2].previousAttempts.last()
        assertContains(note.failure, "repeated attempt 1")
        assertContains(note.failure, "no progress", message = "and why attempt 1 was rejected")
        assertEquals(listOf(noProgress), note.edits)
        assertEquals(listOf(fix), result.attempts[1].edits, "the attempt records the answer that was judged")
        assertEquals(2, adapter.verifyCalls, "attempt 1 and the second answer of attempt 2, not the repeat")
    }

    @Test
    fun `two repeats in one attempt reject it without a build, and it counts as an attempt without progress`() {
        val adapter = FakeAdapter()
        val llm = ScriptedLlm(edits(noProgress), edits(noProgress), edits(noProgress))

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        assertEquals(2, result.attempts.size)
        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts[1].outcome)
        assertContains(rejected.reason, "repeated attempt 1")
        assertEquals(StopReason.NO_PROGRESS, result.stopReason)
        assertEquals(1, adapter.verifyCalls, "only attempt 1 was built")
        assertEquals(3, llm.requests.size)
    }

    @Test
    fun `an answer is a repeat whatever the order of its edits and the whitespace at line ends, and of any earlier attempt`() {
        val one = Edit("App.java", "class App {", "class App { // one")
        val two = Edit("App.java", "broken();", "broken(); // two")
        val other = Edit("App.java", "broken();", "broken(); // other")
        val reordered =
            listOf(two.copy(search = "broken();  ", replace = "broken(); // two   "), one.copy(replace = "class App { // one\t"))
        val adapter = FakeAdapter()
        val llm =
            ScriptedLlm(
                edits(one, two),
                edits(other),
                { Proposal.Edits(reordered, "Again") },
                edits(fix),
            )

        val result = loop(adapter, llm, Budgets(attemptsWithoutProgress = 3)).run(repo, context, adapter.initial())

        assertEquals(listOf(0.0, 0.3, 0.3, 0.7), llm.requests.map { it.temperature })
        assertContains(
            llm.requests[3]
                .previousAttempts
                .last()
                .failure,
            "repeated attempt 1",
        )
        assertIs<AttemptOutcome.Accepted>(result.attempts.last().outcome)
        assertEquals(3, adapter.verifyCalls, "attempts 1 and 2 and the fix, not the repeat")
    }

    @Test
    fun `after an accepted attempt, an answer that was rejected before is built again`() {
        repo.resolve("App.java").writeText("class App {\n    void run() {\n        broken();\n        broken();\n    }\n}\n")
        git("commit", "--quiet", "-am", "two calls")
        val comment = Edit("App.java", "class App {", "class App { // try")
        val fixFirst = Edit("App.java", "        broken();\n        broken();", "        fixed();\n        broken();")
        val adapter = FakeAdapter()
        val llm = ScriptedLlm(edits(comment), edits(fixFirst), edits(comment))

        val result = loop(adapter, llm, Budgets(attempts = 3)).run(repo, context, adapter.initial())

        assertIs<AttemptOutcome.Accepted>(result.attempts[1].outcome)
        assertEquals(3, llm.requests.size, "no second request in attempt 3")
        assertTrue(llm.requests[2].previousAttempts.none { "repeated" in it.failure }, "no repeat note")
        assertEquals(3, adapter.verifyCalls, "attempt 3 was built")
    }

    @Test
    fun `an invalid answer is never a repeat`() {
        val adapter = FakeAdapter()
        val invalid: (FixRequest) -> Proposal = { Proposal.Invalid("the answer is not JSON") }
        val llm = ScriptedLlm(invalid, invalid)

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        assertEquals(2, result.attempts.size)
        assertEquals(2, llm.requests.size, "no second request in attempt 2")
        assertEquals(listOf(0.0, 0.3), llm.requests.map { it.temperature })
        result.attempts.forEach {
            assertEquals(AttemptOutcome.Rejected("the model's answer is invalid: the answer is not JSON"), it.outcome)
        }
    }

    @Test
    fun `a different second answer that is rejected says that the first answer repeated an attempt`() {
        val other = Edit("App.java", "broken();", "broken(); // other")
        val adapter = FakeAdapter()
        val llm = ScriptedLlm(edits(noProgress), edits(noProgress), edits(other))

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts[1].outcome)
        assertTrue(rejected.reason.startsWith("the first answer repeated attempt 1"), rejected.reason)
        assertContains(rejected.reason, "no progress", message = "and why the second answer was rejected")
        assertEquals(listOf(other), result.attempts[1].edits)
        assertEquals(2, adapter.verifyCalls, "attempt 1 and the second answer of attempt 2")
    }

    @Test
    fun `an answer that differs only in indentation is not a repeat`() {
        val indented = noProgress.copy(replace = "  broken(); // still broken")
        val adapter = FakeAdapter()
        val llm = ScriptedLlm(edits(noProgress), edits(indented))

        loop(adapter, llm).run(repo, context, adapter.initial())

        assertEquals(2, llm.requests.size, "no re-ask")
        assertEquals(2, adapter.verifyCalls, "both answers were built")
    }
}
