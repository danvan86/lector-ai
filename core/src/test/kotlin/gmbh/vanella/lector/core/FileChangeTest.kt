package gmbh.vanella.lector.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileChangeTest {
    @Test
    fun `an unchanged file has no hunks`() {
        assertEquals(emptyList(), change("a\nb\n", "a\nb\n").hunks)
    }

    @Test
    fun `a changed line is one removed and one added line`() {
        val change = change("a\nb\nc\n", "a\nB\nc\n")

        assertEquals(listOf(Hunk(2..2, 2..2)), change.hunks)
        assertEquals(listOf(2), change.addedLines)
        assertEquals(2, change.changedLines)
    }

    @Test
    fun `an insertion removes nothing, and a deletion adds nothing`() {
        assertEquals(listOf(Hunk(IntRange(2, 1), 2..3)), change("a\nc\n", "a\nb1\nb2\nc\n").hunks)
        assertEquals(listOf(Hunk(2..3, IntRange(2, 1))), change("a\nb1\nb2\nc\n", "a\nc\n").hunks)
    }

    @Test
    fun `changes far apart are separate hunks, and common lines between them are kept`() {
        val change = change("a\nb\nc\nd\ne\n", "A\nb\nc\nX\nd\nE\n")

        assertEquals(listOf(Hunk(1..1, 1..1), Hunk(IntRange(4, 3), 4..4), Hunk(5..5, 6..6)), change.hunks)
        assertEquals(listOf(1, 4, 6), change.addedLines)
        assertEquals(5, change.changedLines)
    }

    @Test
    fun `a change touches a range that contains an added line`() {
        val change = change("a\nb\nc\nd\n", "a\nb\nC\nd\n")

        assertTrue(change.touches(LineRange(3, 3)))
        assertTrue(change.touches(LineRange(1, 3)))
        assertTrue(change.touches(LineRange(3, 4)))
        assertFalse(change.touches(LineRange(1, 2)))
        assertFalse(change.touches(LineRange(4, 4)))
    }

    @Test
    fun `a deletion touches a range that spans the place it removed lines from`() {
        val emptied = change("void run() {\n    broken();\n}\n", "void run() {\n}\n")

        assertTrue(emptied.touches(LineRange(1, 2)))
        assertFalse(emptied.touches(LineRange(1, 1)))
        assertFalse(emptied.touches(LineRange(2, 2)))
    }

    @Test
    fun `two files of thousands of different lines are one hunk, without comparing every pair`() {
        val before = List(2100) { "old $it" }.joinToString("\n")
        val after = List(2100) { "new $it" }.joinToString("\n")

        assertEquals(listOf(Hunk(1..2100, 1..2100)), change(before, after).hunks)
    }

    private fun change(
        before: String,
        after: String,
    ) = FileChange("App.java", before, after) { _, _ -> null }
}
