package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class RootCommandTest {
    @Test
    fun `root command is lector with the given version`() {
        val result = rootCommand("1.2.3").test("--version")

        assertEquals("1.2.3\n", result.stdout)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `help lists the verify command`() {
        val result = rootCommand("1.2.3").test("--help")

        assertContains(result.stdout, "Commands:")
        assertContains(result.stdout, "verify")
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `verify detects Maven repositories`(
        @TempDir repo: Path,
    ) {
        repo.resolve("pom.xml").createFile()

        // Detection passes, so verify gets as far as the base ref, which fails before Maven runs.
        val result = rootCommand("1.2.3").test("verify $repo --base no-such-ref")

        assertContains(result.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertEquals(2, result.statusCode)
    }
}
