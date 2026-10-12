package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.writeText
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
    fun `help lists the verify, migrate, and heal commands`() {
        val result = rootCommand("1.2.3").test("--help")

        assertContains(result.stdout, "Commands:")
        assertContains(result.stdout, "verify")
        assertContains(result.stdout, "migrate")
        assertContains(result.stdout, "heal")
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

    @Test
    fun `migrate detects Maven repositories`(
        @TempDir repo: Path,
    ) {
        repo.resolve("pom.xml").createFile()

        val result = rootCommand("1.2.3").test("migrate $repo --package g:a --from 1.0 --to 2.0 --base no-such-ref")

        assertContains(result.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `heal detects Maven repositories`(
        @TempDir repo: Path,
    ) {
        repo.resolve("pom.xml").createFile()

        val result = rootCommand("1.2.3").test("heal $repo --package g:a --from 1.0 --to 2.0 --base no-such-ref")

        assertContains(result.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `pr reads the PR from the event with the GitHub adapter and checks it against the Renovate source`(
        @TempDir dir: Path,
    ) {
        val event = dir.resolve("event.json")
        val head = """{"ref": "feature/x", "sha": "1", "repo": {"full_name": "acme/app"}}"""
        val base = """{"sha": "2", "repo": {"full_name": "acme/app"}}"""
        event.writeText(
            """{"pull_request": {"number": 1, "body": null, "user": {"login": "renovate[bot]"}, "head": $head, "base": $base}}""",
        )

        val result =
            rootCommand("1.2.3").test(
                "pr $dir --event $event --bot-name b --bot-email b@example.com",
                envvars = mapOf("LECTOR_GITHUB_TOKEN" to "t"),
            )

        assertEquals("Skipped: no update source recognizes the branch feature/x\n", result.stdout)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `the installed JDKs come from LECTOR_JDK_ variables, by release`() {
        val environment =
            mapOf(
                "LECTOR_JDK_17" to "/opt/java/17",
                "LECTOR_JDK_25" to "/opt/java/openjdk",
                "LECTOR_JDK_LATEST" to "/opt/java/latest",
                "JAVA_HOME" to "/opt/java/openjdk",
                "21" to "/opt/java/21",
            )

        assertEquals(mapOf(17 to Path.of("/opt/java/17"), 25 to Path.of("/opt/java/openjdk")), installedJdks(environment))
    }
}
