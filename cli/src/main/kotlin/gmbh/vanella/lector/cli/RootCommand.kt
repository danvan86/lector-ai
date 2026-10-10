package gmbh.vanella.lector.cli

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("gmbh.vanella.lector.cli.Main")

/** The composition root: builds the command tree. Later milestones wire the adapters in here. */
internal fun rootCommand(version: String): LectorCommand {
    logger.debug("Starting lector {}", version)
    return LectorCommand(version)
}
