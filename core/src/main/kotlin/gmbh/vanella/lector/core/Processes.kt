package gmbh.vanella.lector.core

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.time.Duration

private val logger = LoggerFactory.getLogger("gmbh.vanella.lector.core.Processes")

/** A failure of the tool, not of the repository: a missing program, a timeout, an invalid argument. */
open class ToolError(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** A process ran longer than its timeout and was killed. */
class ProcessTimedOut(
    message: String,
) : ToolError(message)

data class ProcessResult(
    val exitCode: Int,
    val output: String,
)

// What a build or test needs from the environment: the system, the JDK, locale, proxies, and Maven's own variables
// (options, and the credentials a settings file reads with `${env.…}`). Never a token or an action input.
private val ALLOWED_VARIABLES =
    setOf(
        "PATH",
        "HOME",
        "USER",
        "TMPDIR",
        "TZ",
        "LANG",
        "LANGUAGE",
        "JAVA_HOME",
        "CI",
        "HTTP_PROXY",
        "HTTPS_PROXY",
        "NO_PROXY",
        "http_proxy",
        "https_proxy",
        "no_proxy",
    )
private val ALLOWED_PREFIXES = listOf("LC_", "MAVEN_")

/**
 * Runs [command] in [workDir] and returns its exit code and output, with stdout and stderr merged.
 *
 * The output goes to a temporary file, so a full pipe can't block the process. Stdin is closed, so a process that waits
 * for input ends instead of hanging. A process that runs longer than [timeout] is killed, with its children.
 *
 * The process doesn't inherit the tool's environment: builds and tests run code from the updated dependency, so they
 * must not read the push token or the action's inputs. It gets the allowlisted variables of [inherited], and
 * [environment] on top.
 */
fun runProcess(
    command: List<String>,
    workDir: Path,
    timeout: Duration,
    environment: Map<String, String> = emptyMap(),
    inherited: Map<String, String> = System.getenv(),
): ProcessResult {
    logger.info("Running {} in {}", command.joinToString(" "), workDir)
    val log = Files.createTempFile("lector-process", ".log")
    try {
        val process =
            try {
                val builder = ProcessBuilder(command)
                builder.environment().apply {
                    clear()
                    putAll(inherited.filterKeys(::allowed) + environment)
                }
                builder
                    .directory(workDir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start()
            } catch (e: IOException) {
                throw ToolError("Cannot run ${command.first()}: ${e.message}", e)
            }
        process.outputStream.close()
        if (!process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly().waitFor()
            throw ProcessTimedOut("${command.first()} did not finish within $timeout")
        }
        val output = log.readText()
        logger.debug("{} exited with {}:\n{}", command.first(), process.exitValue(), output)
        return ProcessResult(process.exitValue(), output)
    } finally {
        log.deleteIfExists()
    }
}

private fun allowed(name: String) = name in ALLOWED_VARIABLES || ALLOWED_PREFIXES.any { name.startsWith(it) }
