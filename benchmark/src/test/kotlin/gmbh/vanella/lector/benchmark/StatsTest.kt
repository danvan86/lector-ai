package gmbh.vanella.lector.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsTest {
    @Test
    fun `the hypothesis holds at a mean recipe share of 90 percent`() {
        val verdict = verdict(listOf(run(Mode.RECIPE_AND_FIX_LOOP, recipeLines = 9, fixLines = 1)))

        assertEquals(Verdict(90.0, 1), verdict)
        assertTrue(verdict!!.holds)
    }

    @Test
    fun `the hypothesis doesn't hold at 89 percent`() {
        val verdict = verdict(listOf(run(Mode.RECIPE_AND_FIX_LOOP, recipeLines = 89, fixLines = 11)))

        assertEquals(Verdict(89.0, 1), verdict)
        assertFalse(verdict!!.holds)
    }

    @Test
    fun `the share is the mean over runs, rounded to the shown decimal, so the verdict matches the number`() {
        val runs = List(3) { run(Mode.RECIPE_AND_FIX_LOOP, recipeLines = 899_996, fixLines = 100_004) }

        assertEquals(Verdict(90.0, 3), verdict(runs))
        assertEquals(Verdict(50.0, 2), verdict(listOf(run(Mode.RECIPE_AND_FIX_LOOP, 1, 0), run(Mode.RECIPE_AND_FIX_LOOP, 0, 1))))
    }

    @Test
    fun `only green recipe + fix-loop runs that changed lines count for the verdict`() {
        val runs =
            listOf(
                run(Mode.RECIPE_ONLY, recipeLines = 10),
                run(Mode.FIX_LOOP_ONLY, fixLines = 10),
                run(Mode.RECIPE_AND_FIX_LOOP, recipeLines = 0, fixLines = 10, green = false),
                run(Mode.RECIPE_AND_FIX_LOOP, recipeLines = 0, fixLines = 0),
            )

        assertNull(verdict(runs))
        assertEquals(Verdict(100.0, 1), verdict(runs + run(Mode.RECIPE_AND_FIX_LOOP, recipeLines = 5)))
    }

    @Test
    fun `the green rate is taken per repetition, then its mean and spread`() {
        val runs =
            listOf(
                run(Mode.FIX_LOOP_ONLY, repetition = 1, green = true),
                run(Mode.FIX_LOOP_ONLY, repetition = 1, green = true),
                run(Mode.FIX_LOOP_ONLY, repetition = 2, green = true),
                run(Mode.FIX_LOOP_ONLY, repetition = 2, green = false),
                run(Mode.FIX_LOOP_ONLY, repetition = 3, green = false),
                run(Mode.FIX_LOOP_ONLY, repetition = 3, green = false),
                run(Mode.RECIPE_ONLY, green = true),
            )

        assertEquals(Spread(50.0, 0.0, 100.0), greenRate(runs, Mode.FIX_LOOP_ONLY))
        assertEquals(Spread(100.0, 100.0, 100.0), greenRate(runs, Mode.RECIPE_ONLY))
        assertNull(greenRate(runs, Mode.RECIPE_AND_FIX_LOOP))
    }

    @Test
    fun `a case with an invalid run is left out of the numbers, and so are runs without metrics`() {
        val results = Results.decode(fixture("results.json"))

        val counted = counted(results)

        assertEquals(listOf("jackson-3"), invalidCases(results))
        assertEquals(setOf("guava-21", "boot-3"), counted.map { it.first.case }.toSet())
        assertEquals(12, counted.size, "two of the 14 runs of the valid cases have no metrics")
    }

    private fun run(
        mode: Mode,
        recipeLines: Int = 0,
        fixLines: Int = 0,
        green: Boolean = true,
        repetition: Int = 1,
    ) = RunKey("case", mode, repetition) to metrics(green, recipeLines, fixLines)
}

internal fun metrics(
    green: Boolean,
    recipeLines: Int = 0,
    fixLines: Int = 0,
) = Metrics(
    green = green,
    before = Stages(0, 1, 0),
    after = Stages(0, if (green) 0 else 1, 0),
    recipes = emptyList(),
    attempts = 0,
    acceptedFixes = 0,
    rejectionsPerRule = emptyMap(),
    stopReason = null,
    runtime = Runtime(0, 0, 0, 0),
    recipeLines = recipeLines,
    fixLines = fixLines,
    preExisting = 0,
    flaky = 0,
    unattributed = 0,
    testsRun = 1,
    requestsWithDiagnosticsLeftOut = 0,
)

internal fun fixture(name: String) =
    checkNotNull(StatsTest::class.java.getResource("/fixtures/results/$name")) { "fixture $name is missing" }.readText()
