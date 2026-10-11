package gmbh.vanella.lector.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceFactsTest {
    @Test
    fun `a range contains its first and last line, and nothing outside`() {
        assertTrue(3 in LineRange(3, 5))
        assertTrue(5 in LineRange(3, 5))
        assertFalse(2 in LineRange(3, 5))
        assertFalse(6 in LineRange(3, 5))
    }

    @Test
    fun `a range reads as one line or first-last`() {
        assertEquals("3", LineRange(3, 3).toString())
        assertEquals("3-5", LineRange(3, 5).toString())
    }

    @Test
    fun `facts that a language doesn't report are empty`() {
        val assertion = Fragment(LineRange(3, 3), "assertEquals(1, run())")
        val facts =
            SourceFacts(
                methods = listOf(Method("AppTest", "run", LineRange(2, 4))),
                tests = listOf(TestMethod("AppTest", "run", LineRange(2, 4), listOf(assertion))),
            )

        assertEquals(listOf(assertion), facts.tests.single().assertions)
        assertEquals(SourceFacts(facts.methods, facts.tests, emptyList(), emptyList(), emptyList(), emptyList()), facts)
    }
}
