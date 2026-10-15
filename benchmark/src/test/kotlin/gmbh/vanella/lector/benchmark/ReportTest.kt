package gmbh.vanella.lector.benchmark

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ReportTest {
    private val results = Results.decode(fixture("results.json"))
    private val review = Review.decode(fixture("review.json"))

    @Test
    fun `the report on the fixture shows every number the spec asks for`() {
        assertEquals(fixture("report.md"), Report.render(results, review))
    }

    @Test
    fun `the report says that the fix requests carried no release notes`() {
        assertContains(
            Report.render(results, review),
            "The fix requests carry no release notes. `lector pr` passes the notes of a Renovate PR's body, so for those " +
                "cases the fix-loop numbers are a lower bound.",
        )
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
    fun `a review that covers a run twice, or fewer runs than the sample needs, is rejected`() {
        val first = review.runs.first()

        assertEquals(
            "The review covers ${first.key} more than once",
            assertFailsWith<IllegalArgumentException> { Report.render(results, review.copy(runs = review.runs + first)) }.message,
        )
        assertEquals(
            "The review covers 5 runs, not the 6 it needs: at least 10 green runs of the fix-loop modes, or all if there are fewer",
            assertFailsWith<IllegalArgumentException> { Report.render(results, review.copy(runs = review.runs.drop(1))) }.message,
        )
    }

    @Test
    fun `a review of 10 runs is enough when there are more green runs`() {
        val green = List(12) { RunResult("case-$it", Mode.FIX_LOOP_ONLY, 1, "abc", "m", RunStatus.VALID, null, metrics(true), MAVEN) }
        val reviewed = green.map { ReviewedRun(it.case, it.mode, 1, true, "fine") }
        val many = results.copy(runs = green)

        assertContains(Report.render(many, Review("A. Reviewer", reviewed.take(10))), "10 of 10 reviewed green runs (of 12")
        assertFailsWith<IllegalArgumentException> { Report.render(many, Review("A. Reviewer", reviewed.take(9))) }
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

    @Test
    fun `with cases of more than one ecosystem, each gets its own numbers, the JVM first`() {
        val npm =
            results.runs
                .filter { it.case == "boot-3" && it.mode == Mode.FIX_LOOP_ONLY }
                .map { it.copy(case = "zod-4", ecosystem = "npm") }
        val other = npm.map { it.copy(case = "pandas-2", ecosystem = "pypi") }

        val report = Report.render(results.copy(runs = other + npm + results.runs), null)

        val headings = report.lines().filter { it.startsWith("# ") || it == "## 90/10 verdict" || it == "## Recipe step" }
        assertEquals(
            listOf(
                "# Benchmark ${results.date}",
                "# JVM (`maven`)",
                "## 90/10 verdict",
                "# TypeScript (`npm`)",
                "## Recipe step",
                "# pypi (`pypi`)",
                "## Recipe step",
            ),
            headings,
        )
        assertEquals(1, report.split("## Manual review").size - 1, "the review is one for all")
        val typescript = report.substringAfter("# TypeScript").substringBefore("# pypi")
        assertContains(typescript, "None for `npm`: its ecosystem has no recipes, so only the fix loop runs.")
        assertContains(typescript, "| zod-4 | 0/3 |")
        assertContains(typescript, "Green after the fix loop: 0 in at least one run, 0 in every run.")
        assertContains(typescript, "| Case | Fix loop only |")
        assertFalse("Recipe only" in typescript, "no recipe mode in its tables")
    }
}
