package gmbh.vanella.lector.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlanTest {
    private fun case(id: String) =
        Case(id, SYNTHETIC, "https://github.com/example/$id.git", "a".repeat(40), "b".repeat(40), emptyList(), null, MAVEN)

    private val corpus = Corpus(listOf(case("one"), case("two")))

    @Test
    fun `the modes that use the model run three times per case, recipe only runs once`() {
        val runs = plan(corpus).filter { it.case == "one" }

        assertEquals(
            listOf(
                RunKey("one", Mode.RECIPE_ONLY, 1),
                RunKey("one", Mode.FIX_LOOP_ONLY, 1),
                RunKey("one", Mode.FIX_LOOP_ONLY, 2),
                RunKey("one", Mode.FIX_LOOP_ONLY, 3),
                RunKey("one", Mode.RECIPE_AND_FIX_LOOP, 1),
                RunKey("one", Mode.RECIPE_AND_FIX_LOOP, 2),
                RunKey("one", Mode.RECIPE_AND_FIX_LOOP, 3),
            ),
            runs,
        )
        assertEquals(14, plan(corpus).size)
    }

    @Test
    fun `a plan can be limited to some cases`() {
        assertEquals(setOf("two"), plan(corpus, setOf("two")).map { it.case }.toSet())
    }

    @Test
    fun `an unknown case in the limit is an error`() {
        val error = assertFailsWith<IllegalArgumentException> { plan(corpus, setOf("two", "three")) }

        assertEquals("No case three in the corpus", error.message)
    }

    @Test
    fun `modes are named as in the results`() {
        assertEquals(listOf("recipe-only", "fix-loop-only", "recipe-and-fix-loop"), Mode.entries.map { it.id })
    }
}
