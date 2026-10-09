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
    fun `no arguments prints the help`() {
        val result = command.test(emptyList())

        assertContains(result.output, "Usage: lector")
        assertContains(result.output, "--version")
    }

    @Test
    fun `help lists no subcommands`() {
        val result = command.test("--help")

        assertEquals(false, result.output.contains("Commands:"))
    }
}
