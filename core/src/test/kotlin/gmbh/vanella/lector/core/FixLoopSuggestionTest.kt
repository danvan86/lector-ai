package gmbh.vanella.lector.core

import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * M11 section 7: when a run ends red, the rejected attempt that got furthest on the last commit is suggested to the
 * reviewer as a diff. It is never committed.
 */
class FixLoopSuggestionTest : FixLoopFixture() {
    private val twoClusters = "class App {\n    void run() {\n        broken();\n        missing();\n    }\n}\n"

    @Test
    fun `a red run suggests the built, guardrail-clean attempt on the last commit with the lowest counts, the later one of a tie`() {
        repo.resolve("App.java").writeText(twoClusters)
        git("commit", "--quiet", "-am", "two clusters")
        val adapter = FakeAdapter()
        val llm =
            ScriptedLlm(
                // 1, on the commit before the fix: built, rejected, 0/2/0 after it, lower than any later one.
                edits(Edit("App.java", "broken();", "broken(); // old")),
                // 2: accepted, the run's last commit (0/1/0).
                edits(fix),
                // 3: breaks a guardrail, never built.
                edits(Edit("App.java", "missing();", "return null;")),
                // 4 and 5: built, 0/3/0 after them.
                edits(Edit("App.java", "missing();", "missing();\n        broken();\n        broken();")),
                edits(Edit("App.java", "missing();", "missing(); // five\n        broken();\n        broken();")),
                // 6: built, 0/4/0 after it.
                edits(Edit("App.java", "missing();", "missing();\n        broken();\n        broken();\n        broken();")),
            )
        val budgets = Budgets(attemptsPerCluster = 5, attempts = 6, attemptsWithoutProgress = 4)

        val result = loop(adapter, llm, budgets).run(repo, context, adapter.initial())

        assertEquals(6, result.attempts.size)
        assertIs<AttemptOutcome.Accepted>(result.attempts[1].outcome)
        val suggestion = assertNotNull(result.suggestion, "the run ends with missing() left")
        assertEquals(5, suggestion.attempt, "the later of the two with the lowest counts on the last commit")
        assertContains(suggestion.diff, "-        missing();")
        assertContains(suggestion.diff, "+        missing(); // five")
        assertContains(suggestion.diff, "App.java")
    }

    @Test
    fun `the suggestion is never committed, and the working tree is clean after the run`() {
        val head = git("rev-parse", "HEAD")
        val adapter = FakeAdapter()
        val first = Edit("App.java", "broken();", "broken(); // first")
        val llm = ScriptedLlm(edits(first), edits(first.copy(replace = "broken(); // suggested")))

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        assertEquals(2, assertNotNull(result.suggestion).attempt, "the later of two with equal counts")
        assertFalse("// suggested" in git("log", "-p", "--all"), "no commit has the suggested edit")
        assertEquals(head, git("rev-parse", "HEAD"), "nothing was committed")
        assertEquals("", git("status", "--porcelain"))
    }

    @Test
    fun `a green run has no suggestion`() {
        val adapter = FakeAdapter()
        val llm = ScriptedLlm(edits(Edit("App.java", "broken();", "broken(); // no")), edits(fix))

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        assertEquals(StopReason.NOTHING_LEFT, result.stopReason)
        assertNull(result.suggestion)
    }

    @Test
    fun `a red run whose attempts were never built has no suggestion`() {
        val adapter = FakeAdapter()
        val llm = ScriptedLlm(edits(Edit("App.java", "brokn();", "fixed();")), edits(Edit("App.java", "broken();", "return null;")))

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        assertEquals(StopReason.NO_PROGRESS, result.stopReason)
        assertNull(result.suggestion, "one couldn't be applied, one broke a guardrail")
    }
}
