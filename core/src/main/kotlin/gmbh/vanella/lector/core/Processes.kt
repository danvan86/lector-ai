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
class ToolError(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

data class ProcessResult(
    val exitCode: Int,
    val output: String,
)

/**
 * Runs [command] in [workDir] and returns its exit code and output, with stdout and stderr merged.
 *
 * The output goes to a temporary file, so a full pipe can't block the process. Stdin is closed, so a process that waits
 * for input ends instead of hanging. A process that runs longer than [timeout] is killed, with its children.
 */
fun runProcess(
    command: List<String>,
    workDir: Path,
    timeout: Duration,
): ProcessResult {
    logger.info("Running {} in {}", command.joinToString(" "), workDir)
    val log = Files.createTempFile("lector-process", ".log")
    try {
        val process =
            try {
                ProcessBuilder(command)
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
            throw ToolError("${command.first()} did not finish within $timeout")
        }
        val output = log.readText()
        logger.debug("{} exited with {}:\n{}", command.first(), process.exitValue(), output)
        return ProcessResult(process.exitValue(), output)
    } finally {
        log.deleteIfExists()
    }
}
