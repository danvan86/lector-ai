package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
    fun `help lists no subcommands`() {
        val result = command.test("--help")

        assertFalse(result.stdout.contains("Commands:"))
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `unknown option fails with 1 and an error on stderr`() {
        val result = command.test("--bogus")

        assertEquals("", result.stdout)
        assertContains(result.stderr, "no such option --bogus")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `unexpected argument fails with 1 and an error on stderr`() {
        val result = command.test("foo")

        assertEquals("", result.stdout)
        assertContains(result.stderr, "got unexpected extra argument (foo)")
        assertEquals(1, result.statusCode)
    }
}
