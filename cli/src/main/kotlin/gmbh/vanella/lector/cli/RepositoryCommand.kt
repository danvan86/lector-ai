package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import gmbh.vanella.lector.core.BaseCommit
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.Verification
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/** What a command prints, in both formats, and the verification its exit code follows. */
internal class Report(
    val text: String,
    val json: JsonObject,
    val verification: Verification,
)

/**
 * A pipeline stage that works on a repository: finds the adapter for its ecosystem, resolves the base commit, prints the
 * report, and exits with the verdict. `1` only if the update caused a problem. `2` if the tool failed or was called
 * wrongly. Pre-existing, flaky, and unattributed failures exit with `0`.
 */
internal abstract class RepositoryCommand(
    name: String,
    private val adapters: (mavenSettings: Path?) -> List<EcosystemAdapter>,
    private val baseCommit: (repo: Path, ref: String) -> BaseCommit,
) : CliktCommand(name = name) {
    protected val repo by argument(help = "The repository").path()
    private val base by option(
        "--base",
        metavar = "<git-ref>",
        help = "The commit before the update. Failing tests are run there too, to tell new failures from old ones.",
    )
    private val format by option("--format", help = "Report format").choice("text", "json").default("text")
    private val mavenSettings by option("--maven-settings", metavar = "<file>", help = "Maven settings file, passed to Maven").path()

    /** Runs the stage on [repo]. Any exception is a tool error. */
    protected abstract fun execute(
        adapter: EcosystemAdapter,
        base: BaseCommit?,
    ): Report

    // Exit 1 means "caused by the update". Anything else that goes wrong is the tool's failure, so it must exit with 2.
    @Suppress("TooGenericExceptionCaught")
    final override fun run() {
        val report =
            try {
                report()
            } catch (e: Exception) {
                throw toolError(e)
            }
        echo(if (format == "json") report.json.toString() else report.text)
        if (report.verification.causedByUpdate.isNotEmpty()) throw ProgramResult(1)
    }

    private fun toolError(e: Exception): CliktError {
        val message = if (e is ToolError) e.message else "Unexpected failure: $e"
        return CliktError("Error: $message", e, statusCode = TOOL_ERROR)
    }

    private fun report(): Report {
        ensure(repo.isDirectory()) { "$repo is not a directory" }
        ensure(mavenSettings?.isRegularFile() != false) { "Maven settings $mavenSettings is not a file" }
        val adapter =
            adapters(mavenSettings).firstOrNull { it.detect(repo) }
                ?: throw ToolError("No supported ecosystem in $repo. Supported: Maven (pom.xml at the root).")
        // Resolve the base before the build, so a wrong ref fails in seconds, not after a full build.
        val base = base?.let { baseCommit(repo, it) }
        return execute(adapter, base)
    }

    private fun ensure(
        valid: Boolean,
        message: () -> String,
    ) {
        if (!valid) throw ToolError(message())
    }
}
