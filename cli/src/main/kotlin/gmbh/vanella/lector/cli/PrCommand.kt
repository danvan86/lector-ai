package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.path
import gmbh.vanella.lector.core.CommitIdentity
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.GitHost
import gmbh.vanella.lector.core.LlmClient
import gmbh.vanella.lector.core.PrComment
import gmbh.vanella.lector.core.PrOutcome
import gmbh.vanella.lector.core.PrPipeline
import gmbh.vanella.lector.core.PrSettings
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.UpdateSource
import gmbh.vanella.lector.git.GitHubHost
import gmbh.vanella.lector.llm.OllamaClient
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

// The GitHub App's installation token. Only the environment carries it: a command line shows in process lists.
private const val TOKEN_VARIABLE = "LECTOR_GITHUB_TOKEN"
private const val DEFAULT_API_URL = "https://api.github.com"
private const val DEFAULT_SERVER_URL = "https://github.com"

/**
 * `lector pr [<repo>]`: the pipeline step the GitHub Action runs on a dependency PR. Checks the trigger, verifies,
 * applies the recipes and the fix loop, pushes, and reports on the PR. Exit `1` only if something the update caused is
 * left after a run that worked on the PR; a skipped run, a green PR, and a rejected push exit with `0`, a tool error with
 * `2`. Reads the GitHub Actions environment: the event file, the API and server URLs.
 */
internal class PrCommand(
    private val adapters: (mavenSettings: Path?) -> List<EcosystemAdapter>,
    private val sources: List<UpdateSource>,
    private val index: () -> RecipeIndex,
    private val llm: (url: URI, model: String) -> LlmClient = ::OllamaClient,
    private val host: (event: Path, apiUrl: URI, serverUrl: URI, token: String) -> GitHost = ::GitHubHost,
) : CliktCommand(name = "pr") {
    private val repo by argument(help = "The repository, checked out at the PR's head commit").path().default(Path.of("."))
    private val event by option(
        "--event",
        metavar = "<file>",
        envvar = "GITHUB_EVENT_PATH",
        help = "The pull_request event",
    ).path().required()
    private val apiUrl by option("--github-api-url", metavar = "<url>", envvar = "GITHUB_API_URL", help = "GitHub's REST API")
        .httpUrl()
        .default(URI(DEFAULT_API_URL), defaultForHelp = DEFAULT_API_URL)
    private val serverUrl by option(
        "--github-server-url",
        metavar = "<url>",
        envvar = "GITHUB_SERVER_URL",
        help = "GitHub, for fetch and push",
    ).httpUrl()
        .default(URI(DEFAULT_SERVER_URL), defaultForHelp = DEFAULT_SERVER_URL)
    private val botName by option("--bot-name", metavar = "<name>", help = "The GitHub App's bot, the author of every commit")
        .required()
        .check("must not be blank") { it.isNotBlank() }
    private val botEmail by option("--bot-email", metavar = "<email>", help = "The bot's email")
        .required()
        .check("must not be blank") { it.isNotBlank() }
    private val botLogins by option("--bot-logins", metavar = "<logins>", help = "The bots whose PRs are fixed, comma-separated")
        .split(",")
        .default(listOf("renovate[bot]"), defaultForHelp = "renovate[bot]")
    private val mavenSettings by option(
        "--maven-settings",
        metavar = "<file>",
        help = "Maven settings file, passed to Maven. Empty for none.",
    ).path()
    private val fixLoop by FixLoopOptions()

    override fun help(context: Context) =
        "Fixes a dependency PR in CI: verifies it, fixes what the update broke, pushes, and reports. The GitHub App's token " +
            "comes from $TOKEN_VARIABLE. The event and the URLs default to GitHub Actions' GITHUB_EVENT_PATH, GITHUB_API_URL, " +
            "and GITHUB_SERVER_URL."

    // Exit 1 means "caused by the update". Anything else that goes wrong is the tool's failure: exit 2.
    @Suppress("TooGenericExceptionCaught")
    override fun run() {
        val outcome =
            try {
                outcome()
            } catch (e: Throwable) {
                throw toolError(e)
            }
        echo(text(outcome))
        if (outcome is PrOutcome.Reported &&
            outcome.report.verification.causedByUpdate
                .isNotEmpty()
        ) {
            throw ProgramResult(1)
        }
    }

    private fun outcome(): PrOutcome {
        val token = currentContext.readEnvvar(TOKEN_VARIABLE).orEmpty()
        ensure(token.isNotBlank()) { "Set $TOKEN_VARIABLE to the GitHub App's installation token" }
        ensure(repo.isDirectory()) { "$repo is not a directory" }
        // The action passes an empty value when no settings file is configured.
        val settings = mavenSettings?.takeIf { it.toString().isNotEmpty() }
        ensure(settings?.isRegularFile() != false) { "Maven settings $settings is not a file" }
        val pipeline =
            PrPipeline(
                host = host(event, apiUrl, serverUrl, token),
                sources = sources,
                adapters = adapters(settings),
                index = index(),
                llm = llm(fixLoop.ollamaUrl, fixLoop.model),
                settings = PrSettings(botLogins.toSet(), CommitIdentity(botName, botEmail), fixLoop.budgets()),
            )
        return pipeline.run(repo)
    }

    private fun text(outcome: PrOutcome) =
        when (outcome) {
            is PrOutcome.Skipped -> {
                "Skipped: ${outcome.reason}"
            }

            is PrOutcome.Green -> {
                "Verify is green: nothing caused by the update. lector-ai changed nothing.\n\n" +
                    TextReport.render(outcome.verification)
            }

            is PrOutcome.PushRejected -> {
                "The push was rejected: the branch moved on. The new head gets its own run."
            }

            is PrOutcome.Reported -> {
                PrComment.render(outcome.report)
            }
        }
}
