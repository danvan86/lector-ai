package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.options.versionOption

/** Exit code when the tool fails or is called wrongly. `1` is reserved for "caused by the update". */
internal const val TOOL_ERROR = 2

/** The `lector` root command. Its subcommands are wired in [rootCommand]. */
class LectorCommand(
    version: String,
) : NoOpCliktCommand(name = "lector") {
    override val printHelpOnEmptyArgs = true

    init {
        versionOption(version, message = { it })
    }

    override fun help(context: Context) = "Fixes the build and test errors that a dependency update causes."

    /**
     * Runs the command line like Clikt's `main`, except that a usage error exits with [TOOL_ERROR]. Clikt's default, `1`,
     * would tell the pipeline that the update caused a problem.
     */
    fun execute(argv: List<String>) {
        try {
            parse(argv)
        } catch (e: UsageError) {
            exit(e, TOOL_ERROR)
        } catch (e: CliktError) {
            exit(e, e.statusCode)
        }
    }

    private fun exit(
        error: CliktError,
        status: Int,
    ) {
        echoFormattedHelp(error)
        currentContext.exitProcess(status)
    }
}
