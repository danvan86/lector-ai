package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.context

/** What [LectorCommand.execute] wrote and the exit code it asked for. `0` when it didn't exit, as the JVM would. */
internal data class Execution(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/**
 * Runs [LectorCommand.execute] in-process: output and exit go to the [Execution] instead of the terminal and the JVM.
 * Options read no environment variables, like Clikt's `test()`: on a GitHub Actions runner, `GITHUB_EVENT_PATH` would
 * fill `--event`.
 */
internal fun LectorCommand.executeInProcess(vararg argv: String): Execution {
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    var exitCode = 0
    context {
        echoMessage = { _, message, trailingNewline, err ->
            (if (err) stderr else stdout).append(message).append(if (trailingNewline) "\n" else "")
        }
        exitProcess = { exitCode = it }
        readEnvvar = { null }
    }
    execute(argv.asList())
    return Execution(exitCode, stdout.toString(), stderr.toString())
}
