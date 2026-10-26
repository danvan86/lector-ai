package gmbh.vanella.lector.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnmaskingTest {
    private val sent = Diagnostic("A.java", 3, 9, null, "cannot find symbol a", Severity.ERROR, "javac")
    private val uncovered = Diagnostic("A.java", 4, 9, null, "cannot find symbol b", Severity.ERROR, "javac")

    @Test
    fun `a sent error replaced by one in an unchanged line is uncovered`() {
        assertTrue(uncovers(listOf(sent), listOf(sent), listOf(uncovered), mapOf("A.java" to setOf(3)), emptySet()))
    }

    @Test
    fun `a new error without a line is not in a changed line`() {
        val anywhere = uncovered.copy(line = null)

        assertTrue(uncovers(listOf(sent), listOf(sent), listOf(anywhere), mapOf("A.java" to setOf(3)), emptySet()))
    }

    @Test
    fun `a new error in a changed line of another file is not in a changed line`() {
        assertTrue(uncovers(listOf(sent), listOf(sent), listOf(uncovered), mapOf("B.java" to setOf(4)), emptySet()))
    }

    @Test
    fun `more test failures than before are no progress`() {
        val failure = Diagnostic(null, null, null, null, "expected 1", Severity.ERROR, "surefire", "com.example.ATest#a")
        val other = failure.copy(test = "com.example.ATest#b")

        assertFalse(uncovers(listOf(sent), listOf(sent, failure), listOf(failure, other), emptyMap(), emptySet()))
    }

    @Test
    fun `more build errors than before are no progress, even if each was there before`() {
        val build = Diagnostic(null, null, null, null, "Failed to execute goal", Severity.ERROR, "maven")

        assertFalse(uncovers(listOf(sent), listOf(sent, build), listOf(build, build), emptyMap(), emptySet()))
    }

    @Test
    fun `a sent build error is never uncovered`() {
        val build = Diagnostic(null, null, null, null, "Failed to execute goal", Severity.ERROR, "maven")

        assertFalse(uncovers(listOf(build), listOf(build, sent), listOf(uncovered), emptyMap(), emptySet()))
    }
}
