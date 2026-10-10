package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LectorCommandTest {
    private val command = LectorCommand(version = "1.2.3")

    @Test
    fun `--version prints only the version`() {
        val result = command.test("--version")

        assertEquals("1.2.3\n", result.stdout)
        assertEquals("", result.stderr)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `no arguments prints the help to stdout`() {
        val result = command.test(emptyList())

        assertContains(result.stdout, "Usage: lector")
        assertContains(result.stdout, "--version")
        assertEquals("", result.stderr)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `execute prints the version and doesn't exit with an error`() {
        val run = command.executeInProcess("--version")

        assertEquals(Execution(0, "1.2.3\n", ""), run)
    }

    @Test
    fun `execute exits with 2 on an unknown option, because 1 means caused by the update`() {
        val run = command.executeInProcess("--bogus")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "Usage: lector")
        assertContains(run.stderr, "no such option --bogus")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `execute exits with 2 on an unexpected argument`() {
        val run = command.executeInProcess("foo")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "got unexpected extra argument (foo)")
        assertEquals(2, run.exitCode)
    }
}
