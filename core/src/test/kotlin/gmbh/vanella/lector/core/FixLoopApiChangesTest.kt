package gmbh.vanella.lector.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the model reads first (M10): the API changes of every change of the PR come before any full API, so trimming
 * the request cuts the full APIs first. Two changes of one group that say the same are told once.
 */
class FixLoopApiChangesTest : FixLoopFixture() {
    private val other = DependencyChange("test", "org.example:other", "2.0", "3.0", UpdateType.MAJOR)

    @Test
    fun `the API changes of all changes come before their full APIs`() {
        val llm = ScriptedLlm(edits(fix))
        val adapter = FakeAdapter(grounding = { change -> "API of ${change.`package`}" })
        adapter.apiChanges = { change -> "API changes of ${change.`package`}" }

        loop(adapter, llm).run(repo, context.copy(changes = listOf(change, other)), adapter.initial())

        assertEquals(
            "API changes of $lib\n\nAPI changes of org.example:other\n\nAPI of $lib\n\nAPI of org.example:other",
            llm.requests.single().grounding,
        )
    }

    @Test
    fun `changes of one group that ground the same are told once`() {
        val llm = ScriptedLlm(edits(fix))
        val adapter = FakeAdapter(grounding = { "API of the group" })
        adapter.apiChanges = { "API changes of the group" }

        loop(adapter, llm).run(repo, context.copy(changes = listOf(change, other)), adapter.initial())

        assertEquals("API changes of the group\n\nAPI of the group", llm.requests.single().grounding)
    }

    @Test
    fun `an adapter without API changes grounds the request exactly as before`() {
        val llm = ScriptedLlm(edits(fix))
        val adapter = FakeAdapter(grounding = { change -> "API of ${change.`package`}" })

        loop(adapter, llm).run(repo, context.copy(changes = listOf(change, other)), adapter.initial())

        assertEquals("API of $lib\n\nAPI of org.example:other", llm.requests.single().grounding)
    }
}
