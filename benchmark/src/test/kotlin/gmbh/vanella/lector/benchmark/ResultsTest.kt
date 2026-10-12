package gmbh.vanella.lector.benchmark

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResultsTest {
    private val planned = listOf(RunKey("a", Mode.RECIPE_ONLY, 1), RunKey("a", Mode.FIX_LOOP_ONLY, 1), RunKey("a", Mode.FIX_LOOP_ONLY, 2))

    private fun run(
        key: RunKey,
        toolCommit: String = "abc",
        model: String = "qwen2.5-coder:7b",
    ) = RunResult(key.case, key.mode, key.repetition, toolCommit, model, RunStatus.VALID, null, metrics(green = true))

    @Test
    fun `merging puts the runs in plan order and marks planned runs without a result as missing`() {
        val results = Results.merge(planned, listOf(run(planned[2]), run(planned[0])), "ubuntu-24.04", "2026-10-12")

        assertEquals("abc", results.toolCommit)
        assertEquals("qwen2.5-coder:7b", results.model)
        assertEquals("ubuntu-24.04", results.runner)
        assertEquals("2026-10-12", results.date)
        assertEquals(planned, results.runs.map { it.key })
        assertEquals(listOf(RunStatus.VALID, RunStatus.MISSING, RunStatus.VALID), results.runs.map { it.status })
        assertEquals(
            RunResult("a", Mode.FIX_LOOP_ONLY, 1, "abc", "qwen2.5-coder:7b", RunStatus.MISSING, "no result", null),
            results.runs[1],
        )
    }

    @Test
    fun `runs of different tool commits or models, or without any run, don't merge`() {
        listOf(
            listOf(run(planned[0]), run(planned[1], toolCommit = "def")),
            listOf(run(planned[0]), run(planned[1], model = "qwen3-coder:30b")),
            emptyList(),
        ).forEach { runs ->
            val error = assertFailsWith<IllegalArgumentException> { Results.merge(planned, runs, "r", "2026-10-12") }
            assertContains(error.message.orEmpty(), "must share one tool commit and one model")
        }
    }

    @Test
    fun `a run that wasn't planned doesn't merge`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                Results.merge(planned, listOf(run(RunKey("b", Mode.RECIPE_ONLY, 1))), "r", "2026-10-12")
            }

        assertEquals("Runs that weren't planned: RunKey(case=b, mode=RECIPE_ONLY, repetition=1)", error.message)
    }

    @Test
    fun `results and runs round-trip through JSON`() {
        val results = Results.merge(planned, listOf(run(planned[0])), "r", "2026-10-12")

        assertEquals(results, Results.decode(results.encode()))
        assertEquals(results.runs[0], Results.decodeRun(Results.encodeRun(results.runs[0])))
        assertContains(results.encode(), "\"mode\": \"recipe-only\"")
        assertContains(results.encode(), "\"status\": \"missing\"")
    }

    @Test
    fun `malformed results, runs, and reviews are rejected`() {
        assertContains(assertFailsWith<IllegalArgumentException> { Results.decode("{}") }.message.orEmpty(), "Malformed results")
        assertContains(assertFailsWith<IllegalArgumentException> { Results.decodeRun("[]") }.message.orEmpty(), "Malformed run result")
        assertContains(assertFailsWith<IllegalArgumentException> { Review.decode("{") }.message.orEmpty(), "Malformed review")
    }

    @Test
    fun `the review records per run whether it would be accepted unchanged, and why`() {
        val review = Review.decode(fixture("review.json"))

        assertEquals("A. Reviewer", review.reviewer)
        val first = review.runs.first()
        assertEquals(RunKey("guava-21", Mode.FIX_LOOP_ONLY, 1), first.key)
        assertEquals(true, first.acceptedUnchanged)
        assertEquals("Uses MoreObjects.firstNonNull, as the release notes say", first.reason)
    }

    @Test
    fun `a missing field anywhere in results or a review is rejected`() {
        val results = withOneFieldLess(Json.parseToJsonElement(fixture("results.json")))
        val reviews = withOneFieldLess(Json.parseToJsonElement(fixture("review.json")))

        results.forEach { assertFailsWith<IllegalArgumentException>(it.toString()) { Results.decode(it.toString()) } }
        reviews.forEach { assertFailsWith<IllegalArgumentException>(it.toString()) { Review.decode(it.toString()) } }
        assertTrue(results.size > 100)
    }

    /** Copies of [element], each with one field of one of its objects left out. Maps (rejections per rule) aren't objects. */
    private fun withOneFieldLess(element: JsonElement): List<JsonElement> =
        when (element) {
            is JsonObject -> {
                element.keys.map { key -> JsonObject(element - key) } +
                    element.entries.filter { it.key != "rejectionsPerRule" }.flatMap { (key, value) ->
                        withOneFieldLess(value).map { JsonObject(element + (key to it)) }
                    }
            }

            is JsonArray -> {
                element.withIndex().flatMap { (index, value) ->
                    withOneFieldLess(value).map { changed -> JsonArray(element.toMutableList().apply { set(index, changed) }) }
                }
            }

            is JsonPrimitive -> {
                emptyList()
            }
        }
}
