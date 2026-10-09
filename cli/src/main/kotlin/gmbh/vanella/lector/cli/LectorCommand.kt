package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.versionOption
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** The `lector` root command. Subcommands (`verify`, …) arrive with later milestones. */
class LectorCommand(
    version: String,
) : CliktCommand(name = "lector") {
    override val printHelpOnEmptyArgs = true

    init {
        logger.debug("Starting lector {}", version)
        versionOption(version, message = { it })
    }

    override fun help(context: Context) = "Fixes the build and test errors that a dependency update causes."

    override fun run() = Unit

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(LectorCommand::class.java)
    }
}
