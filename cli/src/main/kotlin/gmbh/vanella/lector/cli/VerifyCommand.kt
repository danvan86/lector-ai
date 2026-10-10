package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import gmbh.vanella.lector.core.BaseCommit
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.GitBaseCommit
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.Verification
import gmbh.vanella.lector.core.attribute
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * `lector verify <repo>`: builds and tests the repository, attributes every problem, and prints the report.
 * Exits with 1 only if the update caused a problem. Pre-existing, flaky, and unattributed failures are reported with 0.
 */
internal class VerifyCommand(
    private val adapters: (mavenSettings: Path?) -> List<EcosystemAdapter>,
    private val baseCommit: (repo: Path, ref: String) -> BaseCommit = GitBaseCommit::resolve,
) : CliktCommand(name = "verify") {
    private val repo by argument(help = "The repository to verify").path()
    private val base by option(
        "--base",
        metavar = "<git-ref>",
        help = "The commit before the update. Failing tests are run there too, to tell new failures from old ones.",
    )
    private val format by option("--format", help = "Report format").choice("text", "json").default("text")
    private val mavenSettings by option("--maven-settings", metavar = "<file>", help = "Maven settings file, passed to Maven").path()

    override fun help(context: Context) = "Tells whether a dependency update broke the build or the tests, and where."

    // Exit 1 means "caused by the update". Anything else that goes wrong is the tool's failure, so it must exit with 2.
    @Suppress("TooGenericExceptionCaught")
    override fun run() {
        val verification =
            try {
                verify()
            } catch (e: Exception) {
                throw toolError(e)
            }
        echo(if (format == "json") JsonReport.render(verification) else TextReport.render(verification))
        if (verification.causedByUpdate.isNotEmpty()) throw ProgramResult(1)
    }

    private fun toolError(e: Exception): CliktError {
        val message = if (e is ToolError) e.message else "Unexpected failure: $e"
        return CliktError("Error: $message", e, statusCode = TOOL_ERROR)
    }

    private fun verify(): Verification {
        ensure(repo.isDirectory()) { "$repo is not a directory" }
        ensure(mavenSettings?.isRegularFile() != false) { "Maven settings $mavenSettings is not a file" }
        val adapter =
            adapters(mavenSettings).firstOrNull { it.detect(repo) }
                ?: throw ToolError("No supported ecosystem in $repo. Supported: Maven (pom.xml at the root).")
        // Resolve the base before the build, so a wrong ref fails in seconds, not after a full build.
        val base = base?.let { baseCommit(repo, it) }
        return attribute(adapter, repo, base)
    }

    private fun ensure(
        valid: Boolean,
        message: () -> String,
    ) {
        if (!valid) throw ToolError(message())
    }
}
