package gmbh.vanella.lector.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BestRejectedTest {
    @Test
    fun `lower counts stage by stage win, a tie goes to the later attempt, and a higher one is never diffed`() {
        val best = BestRejected()
        val diffed = mutableListOf<Int>()

        listOf(1 to Progress(0, 2, 0), 2 to Progress(0, 1, 5), 3 to Progress(1, 0, 0), 4 to Progress(0, 1, 5)).forEach { (number, counts) ->
            best.offer(number, counts) { "diff $number".also { diffed += number } }
        }

        assertEquals(Suggestion(4, "diff 4"), best.suggestion)
        assertEquals(listOf(1, 2, 4), diffed)
    }

    @Test
    fun `a new commit clears the best`() {
        val best = BestRejected()
        best.offer(1, Progress(0, 1, 0)) { "diff" }

        best.clear()
        best.offer(3, Progress(0, 5, 0)) { "later" }

        assertEquals(Suggestion(3, "later"), best.suggestion)
        best.clear()
        assertNull(best.suggestion)
    }
}
