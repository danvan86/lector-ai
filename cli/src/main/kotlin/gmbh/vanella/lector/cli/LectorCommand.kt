package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.parameters.options.versionOption

/** The `lector` root command. Its subcommands are wired in [rootCommand]. */
class LectorCommand(
    version: String,
) : NoOpCliktCommand(name = "lector") {
    override val printHelpOnEmptyArgs = true

    init {
        versionOption(version, message = { it })
    }

    override fun help(context: Context) = "Fixes the build and test errors that a dependency update causes."
}
