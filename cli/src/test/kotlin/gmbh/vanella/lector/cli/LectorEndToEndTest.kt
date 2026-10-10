package gmbh.vanella.lector.cli

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the installed `lector` start script as a separate process, the way the pipeline calls it. */
@Tag("e2e")
class LectorEndToEndTest {
    private val version = System.getProperty("lector.version")

    @Test
    fun `--version prints the version to stdout and exits with 0`() {
        val run = lector("--version")

        assertEquals("$version\n", run.stdout)
        assertEquals("", run.stderr)
        assertEquals(0, run.exitCode)
    }

    @Test
    fun `no arguments prints the help to stdout and exits with 0`() {
        val run = lector()

        assertContains(run.stdout, "Usage: lector")
        assertContains(run.stdout, "--version")
        assertEquals("", run.stderr)
        assertEquals(0, run.exitCode)
    }

    @Test
    fun `unknown option prints the error to stderr and exits with 2`() {
        val run = lector("--bogus")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "no such option --bogus")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `verify with invalid usage exits with 2`(
        @TempDir repo: Path,
    ) {
        val run = lector("verify", repo.toString(), "--format", "xml")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "invalid value for --format")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `logs go to stderr and keep stdout clean`() {
        val run = lector("--version", javaOpts = "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug")

        assertEquals("$version\n", run.stdout)
        assertContains(run.stderr, "Starting lector $version")
    }

    @Test
    fun `verify of a repository without a supported ecosystem exits with 2`(
        @TempDir repo: Path,
    ) {
        val run = lector("verify", repo.toString())

        assertEquals("", run.stdout)
        assertEquals("Error: No supported ecosystem in $repo. Supported: Maven (pom.xml at the root).\n", run.stderr)
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `verify with an invalid base ref exits with 2 before Maven runs`(
        @TempDir repo: Path,
    ) {
        repo.resolve("pom.xml").createFile()

        val run = lector("verify", repo.toString(), "--base", "no-such-ref", "--format", "json")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertEquals(2, run.exitCode)
    }

    private data class Run(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    // Both streams go to files, so a full pipe buffer can't block the process. The timeout keeps a hanging run from blocking the build.
    private fun lector(
        vararg args: String,
        javaOpts: String = "",
    ): Run {
        val stdout = File.createTempFile("lector", ".out").apply { deleteOnExit() }
        val stderr = File.createTempFile("lector", ".err").apply { deleteOnExit() }
        val process =
            ProcessBuilder(System.getProperty("lector.bin"), *args)
                .redirectOutput(stdout)
                .redirectError(stderr)
                .apply {
                    environment()["JAVA_HOME"] = System.getProperty("java.home")
                    environment()["JAVA_OPTS"] = javaOpts
                }.start()
        val exited = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!exited) process.destroyForcibly()
        assertTrue(exited, "lector did not exit within $TIMEOUT_SECONDS seconds")
        return Run(process.exitValue(), stdout.readText(), stderr.readText())
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
    }
}
