package gmbh.vanella.lector.benchmark

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReportTest {
    private val results = Results.decode(fixture("results.json"))
    private val review = Review.decode(fixture("review.json"))

    @Test
    fun `the report on the fixture shows every number the spec asks for`() {
        assertEquals(fixture("report.md"), Report.render(results, review))
    }

    @Test
    fun `without a review, the report says so`() {
        assertContains(Report.render(results, null), "## Manual review\n\nNot done yet: add `review.json` and generate the report again.")
    }

    @Test
    fun `a review of a run that isn't green, or not from a fix-loop mode, is rejected`() {
        listOf(
            ReviewedRun("guava-21", Mode.FIX_LOOP_ONLY, 3, true, "red"),
            ReviewedRun("boot-3", Mode.RECIPE_ONLY, 1, true, "no fix loop"),
            ReviewedRun("nowhere", Mode.FIX_LOOP_ONLY, 1, true, "no such run"),
            ReviewedRun("guava-21", Mode.RECIPE_AND_FIX_LOOP, 3, true, "an error"),
        ).forEach { reviewed ->
            val error = assertFailsWith<IllegalArgumentException> { Report.render(results, Review("A. Reviewer", listOf(reviewed))) }
            assertEquals("The review covers ${reviewed.key}, which is not a green run of a fix-loop mode", error.message)
        }
    }

    @Test
    fun `without counted runs, there is no verdict, no rate, and no mean`() {
        val invalid = results.copy(runs = results.runs.filter { it.case == "jackson-3" })

        val report = Report.render(invalid, null)

        assertContains(report, "No verdict: no recipe + fix-loop run ended green with changes.")
        assertContains(report, "| Recipe only | 0 | – | – | 0 / 0 / 0 |")
        assertContains(report, "| jackson-3 | invalid | invalid | invalid |")
        assertContains(report, "| Fix loop only | – | – | 0 |  |")
        assertContains(report, "| none | 0 | – |")
        assertContains(report, "| Recipe only | – | – | – | – |")
        assertContains(report, "## Errors and missing runs\n\nNone.")
        assertContains(report, "1 invalid: jackson-3, excluded from the numbers")
    }

    @Test
    fun `a hypothesis that holds says so, and without invalid cases none are named`() {
        val boot = results.copy(runs = results.runs.filter { it.case == "boot-3" })

        val report = Report.render(boot, null)

        assertContains(report, "The hypothesis **holds**: on 2 green recipe + fix-loop runs, the recipe commits changed 95.0% of the lines")
        assertContains(report, "1 cases, 0 invalid.\n")
    }

    @Test
    fun `a mode without a run that has a result shows a dash, and no case is green in every run`() {
        val failed = results.runs.map { if (it.mode == Mode.RECIPE_AND_FIX_LOOP) it.copy(status = RunStatus.ERROR, metrics = null) else it }

        val report = Report.render(results.copy(runs = failed), null)

        assertContains(report, "| guava-21 | 0/1 | 2/3 | – |")
        assertContains(report, "Green after recipe + fix loop: 0 in at least one run, 0 in every run.")
    }
}
