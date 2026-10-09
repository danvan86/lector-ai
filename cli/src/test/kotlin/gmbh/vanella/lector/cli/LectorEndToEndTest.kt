package gmbh.vanella.lector.cli

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Runs the installed `lector` start script as a separate process, the way the pipeline calls it. */
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
    fun `no arguments prints the help`() {
        val run = lector()

        assertContains(run.stdout, "Usage: lector")
        assertContains(run.stdout, "--version")
    }

    @Test
    fun `logs go to stderr and keep stdout clean`() {
        val run = lector("--version", javaOpts = "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug")

        assertEquals("$version\n", run.stdout)
        assertContains(run.stderr, "Starting lector $version")
    }

    private data class Run(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun lector(
        vararg args: String,
        javaOpts: String = "",
    ): Run {
        val process =
            ProcessBuilder(System.getProperty("lector.bin"), *args)
                .apply {
                    environment()["JAVA_HOME"] = System.getProperty("java.home")
                    environment()["JAVA_OPTS"] = javaOpts
                }.start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        return Run(process.waitFor(), stdout, stderr)
    }
}
