package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.path
import gmbh.vanella.lector.core.BranchPolicy
import gmbh.vanella.lector.core.CommitIdentity
import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.GitHost
import gmbh.vanella.lector.core.LlmClient
import gmbh.vanella.lector.core.PrComment
import gmbh.vanella.lector.core.PrOutcome
import gmbh.vanella.lector.core.PrPipeline
import gmbh.vanella.lector.core.PrSettings
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.UpdateSource
import gmbh.vanella.lector.git.GitHubHost
import gmbh.vanella.lector.git.gitHubAppIdentity
import gmbh.vanella.lector.llm.OllamaClient
import gmbh.vanella.lector.sources.DependabotDependency
import gmbh.vanella.lector.sources.DependabotSource
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

// The GitHub App's installation token. Only the environment carries it: a command line shows in process lists.
private const val TOKEN_VARIABLE = "LECTOR_GITHUB_TOKEN"
private const val DEFAULT_API_URL = "https://api.github.com"
private const val DEFAULT_SERVER_URL = "https://github.com"
private const val GITHUB_HOST = "github.com"
private const val DEFAULT_FALLBACK_LABEL = "lector"
private val DEFAULT_BOT_LOGINS = listOf("renovate[bot]", "dependabot[bot]")
private val SOURCE_NAMES = listOf("renovate", "dependabot", "fallback")
private val MODES = mapOf("in-place" to BranchPolicy.IN_PLACE, "companion" to BranchPolicy.COMPANION)

/** What the update sources are built from: the options of the run that concern them. */
internal data class SourceOptions(
    val botLogins: Set<String>,
    val fallbackLabel: String,
    val dependabotMetadata: List<DependabotDependency>?,
)

/**
 * `lector pr [<repo>]`: the pipeline step the GitHub Action runs on a dependency PR. Checks the trigger, verifies,
 * applies the recipes and the fix loop, pushes, and reports on the PR. Exit `1` only if something the update caused is
 * left after a run that worked on the PR; a skipped run, a green PR, and a rejected push exit with `0`, a tool error with
 * `2`. Reads the GitHub Actions environment: the event file, the API and server URLs.
 */
internal class PrCommand(
    private val adapters: (BuildConfig) -> List<EcosystemAdapter>,
    private val sources: (SourceOptions) -> List<UpdateSource>,
    private val index: () -> RecipeIndex,
    private val llm: (url: URI, model: String) -> LlmClient = ::OllamaClient,
    private val host: (event: Path, apiUrl: URI, serverUrl: URI, token: String, botLogin: String) -> GitHost = ::GitHubHost,
    private val appIdentity: (apiUrl: URI, token: String, slug: String) -> CommitIdentity = ::gitHubAppIdentity,
) : CliktCommand(name = "pr") {
    private val repo by argument(help = "The repository, checked out at the PR's head commit").path().default(Path.of("."))
    private val event by option(
        "--event",
        metavar = "<file>",
        envvar = "GITHUB_EVENT_PATH",
        help = "The pull_request event",
    ).path(mustExist = true, canBeDir = false, mustBeReadable = true).required()
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
    private val appSlug by option(
        "--app-slug",
        metavar = "<slug>",
        help = "The GitHub App's slug: the bot `<slug>[bot]` and its email are looked up on github.com. Empty for none.",
    ).check("must not be blank") { it.isEmpty() || it.isNotBlank() }
    private val botName by option(
        "--bot-name",
        metavar = "<name>",
        help = "The GitHub App's bot, the author of every commit, instead of --app-slug. Empty for none.",
    ).check("must not be blank") { it.isEmpty() || it.isNotBlank() }
    private val botEmail by option("--bot-email", metavar = "<email>", help = "The bot's email, with --bot-name. Empty for none.")
        .check("must not be blank") { it.isEmpty() || it.isNotBlank() }
    private val botLogins by option("--bot-logins", metavar = "<logins>", help = "The bots whose PRs are fixed, comma-separated")
        .convert { logins -> logins.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
        .default(DEFAULT_BOT_LOGINS, defaultForHelp = DEFAULT_BOT_LOGINS.joinToString(","))
        .check("must name at least one bot") { it.isNotEmpty() }
    private val source by option(
        "--source",
        metavar = "<name>",
        help = "The update source that must take the PR: ${SOURCE_NAMES.joinToString(", ")}. Empty for the first that recognizes it.",
    ).check("must be one of ${SOURCE_NAMES.joinToString(", ")}") { it.isEmpty() || it in SOURCE_NAMES }
    private val mavenSettings by option(
        "--maven-settings",
        metavar = "<file>",
        envvar = "LECTOR_MAVEN_SETTINGS",
        help = "Maven settings file, passed to Maven. Empty for none.",
    ).path()
    private val gradleInit by option(
        "--gradle-init",
        metavar = "<file>",
        envvar = "LECTOR_GRADLE_INIT",
        help = "Gradle init script, passed to every Gradle run, e.g. for a mirror. Empty for none.",
    ).path()
    private val npmrc by option(
        "--npmrc",
        metavar = "<file>",
        envvar = "LECTOR_NPMRC",
        help = "User-level .npmrc for npm, pnpm, and Yarn classic, e.g. for a registry. Empty for none.",
    ).path()
    private val yarnrc by option(
        "--yarnrc",
        metavar = "<file>",
        envvar = "LECTOR_YARNRC",
        help = "User-level .yarnrc.yml for Yarn Berry, e.g. for a registry. Empty for none.",
    ).path()
    private val mode by option(
        "--mode",
        metavar = "<mode>",
        help = "Where the fixes go: in-place onto the PR's branch, or into a companion PR. Empty for the source's default.",
    ).check("must be in-place or companion") { it.isEmpty() || it in MODES }
    private val fallbackLabel by option(
        "--fallback-label",
        metavar = "<label>",
        help = "The label that opts any other dependency PR of the repository in",
    ).default(DEFAULT_FALLBACK_LABEL)
        .check("must not be blank") { it.isNotBlank() }
    private val dependabotMetadata by option(
        "--dependabot-metadata",
        metavar = "<json>",
        envvar = "LECTOR_DEPENDABOT_METADATA",
        help = "The updated-dependencies-json output of dependabot/fetch-metadata, for a Dependabot PR. Empty for none.",
    ).validate { json ->
        try {
            if (json.isNotBlank()) DependabotSource.parseMetadata(json)
        } catch (e: ToolError) {
            fail(e.message)
        }
    }
    private val fixLoop by FixLoopOptions()

    override fun help(context: Context) =
        "Fixes a dependency PR in CI: verifies it, fixes what the update broke, pushes, and reports. The GitHub App's token " +
            "comes from $TOKEN_VARIABLE. The event and the URLs default to GitHub Actions' GITHUB_EVENT_PATH, GITHUB_API_URL, " +
            "and GITHUB_SERVER_URL."

    // Exit 1 means "caused by the update". Anything else that goes wrong is the tool's failure: exit 2.
    @Suppress("TooGenericExceptionCaught")
    override fun run() {
        val bot = bot()
        val outcome =
            try {
                outcome(bot)
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

    /**
     * How the bot is named: by the App's slug, looked up on GitHub with the token, or by the typed-in pair. Exactly one way,
     * and the slug only on github.com: the noreply domain of another GitHub isn't known. Anything else is a usage error,
     * before anything runs. Empty values count as not given: the action passes every input.
     */
    private fun bot(): (token: String) -> CommitIdentity {
        val slug = appSlug.orEmpty()
        val name = botName.orEmpty()
        val email = botEmail.orEmpty()
        if (slug.isNotEmpty() && name.isEmpty() && email.isEmpty()) return lookUp(slug)
        if (slug.isEmpty() && name.isNotEmpty() && email.isNotEmpty()) return { _ -> CommitIdentity(name, email) }
        val nothing = slug.isEmpty() && name.isEmpty() && email.isEmpty()
        throw usageError(
            if (nothing) {
                "missing option --bot-name and --bot-email, or --app-slug"
            } else {
                "name the bot one way: --app-slug, or both --bot-name and --bot-email"
            },
        )
    }

    /** The bot of the App [slug], looked up with the token. Only on github.com, whose noreply domain is known. */
    private fun lookUp(slug: String): (token: String) -> CommitIdentity {
        if (serverUrl.scheme != "https" || !serverUrl.host.equals(GITHUB_HOST, ignoreCase = true)) {
            throw usageError(
                "--app-slug works on $GITHUB_HOST only, where the bot's email is known. On $serverUrl, name the bot " +
                    "with --bot-name and --bot-email.",
            )
        }
        return { token -> appIdentity(apiUrl, token, slug) }
    }

    // Thrown from run(), not while parsing: without the context, Clikt prints the root command's usage.
    private fun usageError(message: String) = UsageError(message).apply { context = currentContext }

    private fun outcome(bot: (token: String) -> CommitIdentity): PrOutcome {
        val token = currentContext.readEnvvar(TOKEN_VARIABLE).orEmpty()
        ensure(token.isNotBlank()) { "Set $TOKEN_VARIABLE to the GitHub App's installation token" }
        // Before anything is built or fetched: a wrong identity would make the run miss its own commits.
        val identity = bot(token)
        ensure(repo.isDirectory()) { "$repo is not a directory" }
        // The action passes an empty value when no file is configured.
        val settings = mavenSettings?.takeIf { it.toString().isNotEmpty() }
        ensure(settings?.isRegularFile() != false) { "Maven settings $settings is not a file" }
        val init = gradleInit?.takeIf { it.toString().isNotEmpty() }
        ensure(init?.isRegularFile() != false) { "Gradle init script $init is not a file" }
        val npm = npmrc?.takeIf { it.toString().isNotEmpty() }
        ensure(npm?.isRegularFile() != false) { "The npmrc $npm is not a file" }
        val yarn = yarnrc?.takeIf { it.toString().isNotEmpty() }
        ensure(yarn?.isRegularFile() != false) { "The yarnrc $yarn is not a file" }
        return adapters(BuildConfig(settings, init, npm, yarn)).use { all ->
            val pipeline =
                PrPipeline(
                    // The App's bot commits under its login, so its name is also the author of the report comment.
                    host = host(event, apiUrl, serverUrl, token, identity.name),
                    sources = sources(SourceOptions(botLogins.toSet(), fallbackLabel, metadata())),
                    adapters = all,
                    index = index(),
                    llm = llm(fixLoop.ollamaUrl, fixLoop.model),
                    settings =
                        PrSettings(identity, fixLoop.budgets(), source?.ifEmpty { null }, mode?.let(MODES::get)),
                )
            pipeline.run(repo)
        }
    }

    // The action passes an empty value when the workflow ran no fetch-metadata. The option was validated when parsed.
    private fun metadata() = dependabotMetadata?.takeIf { it.isNotBlank() }?.let(DependabotSource::parseMetadata)

    private fun text(outcome: PrOutcome) =
        when (outcome) {
            is PrOutcome.Skipped -> {
                "Skipped: ${outcome.reason}"
            }

            is PrOutcome.Green -> {
                val closed = outcome.closedCompanion?.let { " Closed the companion PR #$it." }.orEmpty()
                "Verify is green: nothing caused by the update. lector-ai changed nothing.$closed\n\n" +
                    TextReport.render(outcome.verification)
            }

            is PrOutcome.Closed -> {
                val closed = outcome.companion?.let { ": closed the companion PR #$it." } ?: ". It has no companion PR."
                "The PR is ${outcome.state.name.lowercase()}$closed"
            }

            is PrOutcome.PushRejected -> {
                "The push was rejected: the branch moved on. The new head gets its own run."
            }

            is PrOutcome.Reported -> {
                PrComment.render(outcome.report) + allChanges(outcome.report.changes)
            }
        }

    // The comment cuts long lists; the CI log has every change, e.g. the transitive ones of a labelled PR.
    private fun allChanges(changes: List<DependencyChange>): String {
        if (changes.size == 1) return ""
        return "\n\nAll ${changes.size} changes:\n" + changes.joinToString("\n") { "- ${it.describe()}" }
    }
}
