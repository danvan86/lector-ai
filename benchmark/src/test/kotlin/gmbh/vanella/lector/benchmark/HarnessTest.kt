package gmbh.vanella.lector.benchmark

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Edit
import gmbh.vanella.lector.core.FixRequest
import gmbh.vanella.lector.core.Proposal
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.ToolError
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class HarnessTest {
    @TempDir
    lateinit var origin: Path

    @TempDir
    lateinit var work: Path

    private val time = TestTimeSource()
    private val tool = ToolUnderTest("0123abc", "qwen2.5-coder:7b")
    private val app =
        """
        class App {
            void run() {
                old(1);
                old(2);
            }
        }
        """.trimIndent() + "\n"
    private lateinit var case: Case

    @BeforeTest
    fun `create a repository whose update breaks two calls`() {
        git(origin, "init", "--quiet")
        git(origin, "config", "uploadpack.allowAnySHA1InWant", "true")
        origin.resolve("App.java").writeText(app)
        origin.resolve("pom.xml").writeText("$LIB 1.0\n")
        git(origin, "add", ".")
        git(origin, "commit", "--quiet", "-m", "base")
        val base = git(origin, "rev-parse", "HEAD").trim()
        origin.resolve("pom.xml").writeText("$LIB 2.0\n")
        git(origin, "commit", "--quiet", "-am", "Update $LIB to 2.0")
        val head = git(origin, "rev-parse", "HEAD").trim()
        case = Case("lib-2", SYNTHETIC, origin.toString(), base, head, listOf(Change(LIB, "1.0", "2.0")), null)
    }

    @Test
    fun `recipe only applies the recipe, verifies, and never asks the model`() {
        val llm = ScriptedLlm()

        val result = harness(llm = llm).run(case, Mode.RECIPE_ONLY, 1, work)

        assertEquals(RunStatus.VALID, result.status)
        val metrics = metrics(result)
        assertFalse(metrics.green)
        assertEquals(Stages(0, 2, 0), metrics.before)
        assertEquals(Stages(0, 1, 0), metrics.after)
        assertEquals(listOf(RecipeRecord("$LIB 1.0 -> 2.0", UPGRADE.name, true, "applied")), metrics.recipes)
        assertEquals(2, metrics.recipeLines)
        assertEquals(0, metrics.fixLines)
        assertEquals(0, metrics.attempts)
        assertNull(metrics.stopReason)
        assertEquals(emptyList(), llm.requests)
    }

    @Test
    fun `a recipe that fixes everything makes recipe only green`() {
        val adapter = FakeAdapter(rewrite = { it.replace("old(", "fixed(") })

        val metrics = metrics(harness(adapter).run(case, Mode.RECIPE_ONLY, 1, work))

        assertTrue(metrics.green)
        assertEquals(Stages(0, 0, 0), metrics.after)
        assertEquals(4, metrics.recipeLines)
    }

    @Test
    fun `fix loop only runs no recipe and lets the model fix everything`() {
        val llm = ScriptedLlm(edits(Edit("App.java", "old(1);\n        old(2);", "fixed(1);\n        fixed(2);")))

        val metrics = metrics(harness(llm = llm).run(case, Mode.FIX_LOOP_ONLY, 2, work))

        assertTrue(metrics.green)
        assertEquals(emptyList(), metrics.recipes)
        assertEquals(0, metrics.recipeLines)
        assertEquals(4, metrics.fixLines)
        assertEquals(1, metrics.attempts)
        assertEquals(1, metrics.acceptedFixes)
        assertEquals("nothing_left", metrics.stopReason)
        assertNull(llm.requests.single().recipeDiff)
    }

    @Test
    fun `recipe and fix loop runs the recipe, then lets the model fix the rest with the recipe's diff`() {
        val llm = ScriptedLlm(edits(Edit("App.java", "old(2);", "fixed(2);")))

        val result = harness(llm = llm).run(case, Mode.RECIPE_AND_FIX_LOOP, 3, work)

        assertEquals(RunKey("lib-2", Mode.RECIPE_AND_FIX_LOOP, 3), result.key)
        val metrics = metrics(result)
        assertTrue(metrics.green)
        assertEquals(Stages(0, 2, 0), metrics.before)
        assertEquals(Stages(0, 0, 0), metrics.after)
        assertEquals(2, metrics.recipeLines)
        assertEquals(2, metrics.fixLines)
        val request = llm.requests.single()
        assertEquals(1, request.diagnostics.size)
        assertContains(request.recipeDiff.orEmpty(), "+        fixed(1);")
    }

    @Test
    fun `the fix loop runs with the product's default budgets`() {
        val miss = edits(Edit("App.java", "nowhere", "x"))
        val llm = ScriptedLlm(miss, miss, miss)

        val metrics = metrics(harness(llm = llm).run(case, Mode.FIX_LOOP_ONLY, 1, work))

        assertEquals(2, metrics.attempts, "two attempts in a row without progress stop the loop")
        assertEquals("no_progress", metrics.stopReason)
        assertFalse(metrics.green)
    }

    @Test
    fun `a rejection is counted by guardrail rule`() {
        val tooLarge = Edit("App.java", "old(1);\n        old(2);", "\n".repeat(60))
        val llm = ScriptedLlm(edits(tooLarge), edits(tooLarge))

        val metrics = metrics(harness(llm = llm).run(case, Mode.FIX_LOOP_ONLY, 1, work))

        assertEquals(mapOf("diff-size" to 2), metrics.rejectionsPerRule)
        assertEquals(0, metrics.requestsWithDiagnosticsLeftOut)
    }

    @Test
    fun `a request that leaves diagnostics out because they don't fit is counted`() {
        val llm =
            ScriptedLlm(
                edits(Edit("App.java", "old(1);", "fixed(1);")),
                edits(Edit("App.java", "old(2);", "fixed(2);")),
                fits = { it.diagnostics.size <= 1 },
            )

        val metrics = metrics(harness(llm = llm).run(case, Mode.FIX_LOOP_ONLY, 1, work))

        assertEquals(1, metrics.requestsWithDiagnosticsLeftOut)
        assertEquals(2, metrics.acceptedFixes)
        assertTrue(metrics.green)
    }

    @Test
    fun `a head commit without a failure caused by the update makes the case invalid, in every mode`() {
        git(origin, "revert", "--quiet", "--no-edit", "HEAD")
        val fixed = case.copy(head = git(origin, "rev-parse", "HEAD").trim())
        val llm = ScriptedLlm()

        Mode.entries.forEach { mode ->
            val result = harness(llm = llm).run(fixed, mode, 1, work.resolve(mode.id).createDirectory())

            assertEquals(RunStatus.INVALID, result.status)
            assertEquals("the head commit has no failure caused by the update", result.reason)
            assertNull(result.metrics)
        }
        assertEquals(emptyList(), llm.requests)
    }

    @Test
    fun `every run records the tool commit and the model`() {
        val result = harness().run(case, Mode.RECIPE_ONLY, 1, work)

        assertEquals("0123abc", result.toolCommit)
        assertEquals("qwen2.5-coder:7b", result.model)
    }

    @Test
    fun `without tests run, no failure left is not green`() {
        val adapter = FakeAdapter(rewrite = { it.replace("old(", "fixed(") }, testsRun = null)

        assertFalse(metrics(harness(adapter).run(case, Mode.RECIPE_ONLY, 1, work)).green)
    }

    @Test
    fun `pre-existing, flaky, and unattributed failures are counted on their own`() {
        val old = Diagnostic(null, null, null, "AssertionError", "expected 1", Severity.ERROR, "surefire", "AppTest#old")
        val flaky = old.copy(test = "AppTest#flaky")
        val adapter = FakeAdapter(rewrite = { it.replace("old(", "fixed(") }, preExisting = listOf(old), flaky = listOf(flaky))

        val metrics = metrics(harness(adapter).run(case, Mode.RECIPE_ONLY, 1, work))

        assertTrue(metrics.green)
        assertEquals(1, metrics.preExisting)
        assertEquals(1, metrics.flaky)
        assertEquals(0, metrics.unattributed)
        assertEquals(3, metrics.testsRun)
    }

    @Test
    fun `runtime is measured in total and per stage`() {
        val adapter = FakeAdapter(onVerify = { time += 10.seconds })
        val llm =
            ScriptedLlm({
                time += 60.seconds
                Proposal.Edits(listOf(Edit("App.java", "old(2);", "fixed(2);")), "Fix it")
            })

        val metrics = metrics(harness(adapter, llm).run(case, Mode.RECIPE_AND_FIX_LOOP, 1, work))

        assertEquals(Runtime(total = 90, verify = 10, recipe = 10, fixLoop = 70), metrics.runtime)
    }

    @Test
    fun `a change without a recipe is recorded with the reason`() {
        val other = case.copy(changes = case.changes + Change("org.example:other", "1.0", "1.1"))

        val metrics = metrics(harness().run(other, Mode.RECIPE_ONLY, 1, work))

        assertEquals(
            RecipeRecord("org.example:other 1.0 -> 1.1", null, false, "no recipe matches"),
            metrics.recipes.first(),
        )
    }

    @Test
    fun `a recipe that changes nothing is recorded as such`() {
        val metrics = metrics(harness(FakeAdapter(rewrite = { it })).run(case, Mode.RECIPE_ONLY, 1, work))

        assertEquals(RecipeRecord("$LIB 1.0 -> 2.0", UPGRADE.name, false, "applied, but changed nothing"), metrics.recipes.single())
        assertEquals(Stages(0, 2, 0), metrics.after)
    }

    @Test
    fun `a tool error ends the run as an error with its message`() {
        val failing = Checkout { _, _ -> throw ToolError("git fetch failed") }

        val result = Harness(listOf(FakeAdapter()), FakeIndex, ScriptedLlm(), failing, tool).run(case, Mode.RECIPE_ONLY, 1, work)

        assertEquals(RunStatus.ERROR, result.status)
        assertEquals("git fetch failed", result.reason)
        assertNull(result.metrics)
    }

    @Test
    fun `a repository no adapter detects is an error`() {
        val result = Harness(emptyList(), FakeIndex, ScriptedLlm(), GitCheckout, tool).run(case, Mode.RECIPE_ONLY, 1, work)

        assertEquals(RunStatus.ERROR, result.status)
        assertEquals("No supported ecosystem in $origin", result.reason)
    }

    @Test
    fun `a repository that can't be fetched is an error`() {
        val result = harness().run(case.copy(repository = origin.resolve("nowhere").toString()), Mode.RECIPE_ONLY, 1, work)

        assertEquals(RunStatus.ERROR, result.status)
        assertContains(result.reason.orEmpty(), "git fetch failed")
    }

    @Test
    fun `changed lines count what was added and removed, and a binary file as nothing`() {
        origin.resolve("logo.png").toFile().writeBytes(byteArrayOf(0, 1, 2, 0, 3))
        origin.resolve("App.java").writeText(app.replace("old(1);", "fixed(1);\n        log();"))
        git(origin, "add", ".")
        git(origin, "commit", "--quiet", "-m", "change")

        assertEquals(3, changedLines(origin, "HEAD"))
    }

    private fun harness(
        adapter: FakeAdapter = FakeAdapter(),
        llm: ScriptedLlm = ScriptedLlm(),
    ) = Harness(listOf(adapter), FakeIndex, llm, GitCheckout, tool, time)

    private fun metrics(result: RunResult): Metrics {
        assertEquals(RunStatus.VALID, result.status, result.reason)
        return checkNotNull(result.metrics)
    }

    private fun edits(vararg edits: Edit): (FixRequest) -> Proposal = { Proposal.Edits(edits.toList(), "Fix it") }
}
