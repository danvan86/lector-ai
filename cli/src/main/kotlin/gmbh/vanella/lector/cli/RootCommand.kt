package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.subcommands
import gmbh.vanella.lector.ecosystems.jvm.MavenAdapter
import gmbh.vanella.lector.recipes.BundledRecipeIndex
import org.slf4j.LoggerFactory
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("gmbh.vanella.lector.cli.Main")

/** The composition root: builds the command tree and wires the concrete adapters into it. */
internal fun rootCommand(version: String): LectorCommand {
    logger.debug("Starting lector {}", version)
    val adapters = { mavenSettings: Path? -> listOf(MavenAdapter(mavenSettings)) }
    return LectorCommand(version).subcommands(
        VerifyCommand(adapters),
        MigrateCommand(adapters, index = { BundledRecipeIndex.load() }),
    )
}
