package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.subcommands
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.UpdateSource
import gmbh.vanella.lector.ecosystems.jvm.GradleAdapter
import gmbh.vanella.lector.ecosystems.jvm.MavenAdapter
import gmbh.vanella.lector.ecosystems.jvm.installedJdks
import gmbh.vanella.lector.recipes.BundledRecipeIndex
import gmbh.vanella.lector.sources.DependabotSource
import gmbh.vanella.lector.sources.FallbackSource
import gmbh.vanella.lector.sources.RenovateSource
import org.slf4j.LoggerFactory
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("gmbh.vanella.lector.cli.Main")

/** The composition root: builds the command tree and wires the concrete adapters into it. */
internal fun rootCommand(version: String): LectorCommand {
    logger.debug("Starting lector {}", version)
    val environment = System.getenv()
    val adapters = { config: BuildConfig -> adapters(config, environment) }
    return LectorCommand(version).subcommands(
        VerifyCommand(adapters),
        MigrateCommand(adapters, index = { BundledRecipeIndex.load() }),
        HealCommand(adapters, index = { BundledRecipeIndex.load() }),
        PrCommand(adapters, sources = ::sources, index = { BundledRecipeIndex.load() }),
    )
}

/** The update sources in the order they are tried: the bots first, by author and branch, then the opt-in label. */
internal fun sources(options: SourceOptions): List<UpdateSource> =
    listOf(
        RenovateSource(options.botLogins),
        DependabotSource(options.botLogins, options.dependabotMetadata),
        FallbackSource(options.fallbackLabel),
    )

/**
 * The ecosystem adapters, configured from the [environment]: the installed JDKs, Gradle's own variables (the Gradle
 * adapter picks them), and the OpenRewrite Gradle plugin the image ships (`LECTOR_REWRITE_GRADLE_PLUGIN`). Gradle comes
 * first: it refuses a repository with a `pom.xml` next to its Gradle files, which Maven would otherwise take.
 */
internal fun adapters(
    config: BuildConfig,
    environment: Map<String, String>,
): List<EcosystemAdapter> {
    val jdks = installedJdks(environment)
    val path = { name: String -> environment[name]?.takeIf { it.isNotBlank() }?.let { Path.of(it) } }
    return listOf(
        GradleAdapter(config.gradleInit, jdks, environment, path("LECTOR_REWRITE_GRADLE_PLUGIN")),
        MavenAdapter(config.mavenSettings, jdks = jdks),
    )
}
