package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class ProcessesTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `returns the exit code and stdout and stderr merged`() {
        val result = runProcess(listOf("sh", "-c", "echo out; echo err >&2; exit 3"), dir, 10.seconds)

        assertEquals(3, result.exitCode)
        assertEquals("out\nerr\n", result.output)
    }

    @Test
    fun `runs in the given directory`() {
        val result = runProcess(listOf("pwd", "-P"), dir, 10.seconds)

        assertEquals(dir.toRealPath().toString(), result.output.trim())
    }

    @Test
    fun `a process that reads stdin gets end of input instead of hanging`() {
        val result = runProcess(listOf("cat"), dir, 10.seconds)

        assertEquals(0, result.exitCode)
        assertEquals("", result.output)
    }

    @Test
    fun `a missing program is a tool error`() {
        val error = assertFailsWith<ToolError> { runProcess(listOf("no-such-program-lector"), dir, 10.seconds) }

        assertContains(error.message, "Cannot run no-such-program-lector")
    }

    @Test
    fun `a process that runs too long is killed and is a tool error`() {
        lateinit var error: ProcessTimedOut
        val elapsed = measureTime { error = assertFailsWith { runProcess(listOf("sleep", "30"), dir, 200.milliseconds) } }

        assertEquals("sleep did not finish within 200ms", error.message)
        assertTrue(elapsed < 10.seconds, "took $elapsed")
    }

    @Test
    fun `a process that runs too long is killed with its children`() {
        assertFailsWith<ToolError> { runProcess(listOf("sh", "-c", "sleep 30 & echo $! > child.pid; wait"), dir, 500.milliseconds) }

        val pid = dir.resolve("child.pid").readText().trim()
        val child = ProcessHandle.of(pid.toLong())
        child.ifPresent { it.onExit().get(5, TimeUnit.SECONDS) }
        assertFalse(child.map { it.isAlive }.orElse(false), "the child process is still running")
    }

    @Test
    fun `a process sees only the allowlisted variables, never a token or an action input`() {
        val inherited =
            mapOf(
                "PATH" to System.getenv("PATH"),
                "HOME" to "/home/runner",
                "JAVA_HOME" to "/opt/java/21",
                "CI" to "true",
                "LANG" to "C.UTF-8",
                "LC_ALL" to "C.UTF-8",
                "HTTPS_PROXY" to "http://proxy:3128",
                "no_proxy" to "localhost",
                "MAVEN_OPTS" to "-Xmx1g",
                "LECTOR_GITHUB_TOKEN" to "ghs_secret",
                "GITHUB_TOKEN" to "ghs_other",
                "INPUT_TOKEN" to "ghs_input",
                "INPUT_MODEL" to "qwen2.5-coder:7b",
                "AWS_SECRET_ACCESS_KEY" to "aws",
            )

        val result = runProcess(listOf("env"), dir, 10.seconds, inherited = inherited)

        val seen =
            result.output
                .lines()
                .filter { it.isNotEmpty() }
                .map { it.substringBefore('=') }
                .toSet()
        assertEquals(
            setOf("PATH", "HOME", "JAVA_HOME", "CI", "LANG", "LC_ALL", "HTTPS_PROXY", "no_proxy", "MAVEN_OPTS"),
            seen - setOf("PWD", "SHLVL", "_", "__CF_USER_TEXT_ENCODING"),
        )
        listOf("ghs_secret", "ghs_other", "ghs_input", "qwen2.5-coder", "aws").forEach { assertFalse(it in result.output, it) }
    }

    @Test
    fun `extra variables are added to the allowlisted ones and win over them`() {
        val inherited = mapOf("PATH" to System.getenv("PATH"), "JAVA_HOME" to "/opt/java/25")

        val result =
            runProcess(
                listOf("sh", "-c", "echo \"\$JAVA_HOME \$GIT_CONFIG_COUNT\""),
                dir,
                10.seconds,
                environment = mapOf("JAVA_HOME" to "/opt/java/17", "GIT_CONFIG_COUNT" to "1"),
                inherited = inherited,
            )

        assertEquals("/opt/java/17 1\n", result.output)
    }
}
