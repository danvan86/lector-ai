package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.subcommands
import gmbh.vanella.lector.ecosystems.jvm.MavenAdapter
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("gmbh.vanella.lector.cli.Main")

/** The composition root: builds the command tree and wires the concrete adapters into it. */
internal fun rootCommand(version: String): LectorCommand {
    logger.debug("Starting lector {}", version)
    return LectorCommand(version).subcommands(
        VerifyCommand(adapters = { mavenSettings -> listOf(MavenAdapter(mavenSettings)) }),
    )
}
