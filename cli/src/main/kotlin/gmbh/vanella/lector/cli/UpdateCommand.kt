package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import gmbh.vanella.lector.core.BaseCommit
import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.EcosystemAdapter
import java.nio.file.Path

/** A stage that works on one dependency update, given as `--package … --from … --to …`. */
internal abstract class UpdateCommand(
    name: String,
    adapters: (mavenSettings: Path?) -> List<EcosystemAdapter>,
    baseCommit: (repo: Path, ref: String) -> BaseCommit,
) : RepositoryCommand(name, adapters, baseCommit) {
    private val packageName by option("--package", metavar = "<package>", help = "The updated package, e.g. group:artifact").required()
    private val from by option("--from", metavar = "<version>", help = "The version before the update").required()
    private val to by option("--to", metavar = "<version>", help = "The version after the update").required()

    /** The change in [adapter]'s ecosystem, with the update type from its version scheme. */
    protected fun change(adapter: EcosystemAdapter) =
        DependencyChange(adapter.ecosystem, packageName, from, to, adapter.versionScheme.updateType(from, to))
}
