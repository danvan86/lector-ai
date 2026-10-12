package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import gmbh.vanella.lector.core.Edit
import gmbh.vanella.lector.core.GitHost
import gmbh.vanella.lector.core.LlmClient
import gmbh.vanella.lector.core.PullRequest
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.RecipeSelection
import gmbh.vanella.lector.core.runProcess
import gmbh.vanella.lector.sources.DependabotDependency
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class PrCommandTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var dir: Path

    private val fix = Edit("App.java", "broken();", "fixed();")
    private val hosts = mutableListOf<List<Any>>()
    private val clients = mutableListOf<Pair<URI, String>>()
    private val settings = mutableListOf<Path?>()
    private val sourceOptions = mutableListOf<SourceOptions>()
    private val host = FakeHost()
    private val token = mapOf("LECTOR_GITHUB_TOKEN" to "ghs_token")
    private val bot = "--bot-name lector-ai[bot] --bot-email 1+lector-ai[bot]@users.noreply.github.com"
    private lateinit var event: Path

    @BeforeTest
    fun `create a Renovate PR whose update broke the code`() {
        git("init", "--quiet")
        repo.resolve("pom.xml").writeText("<parent>3.3.5</parent>\n")
        repo.resolve("App.java").writeText("class App {\n    void run() {\n        broken();\n    }\n}\n")
        git("add", ".")
        git("commit", "--quiet", "-m", "base")
        val base = git("rev-parse", "HEAD").trim()
        repo.resolve("pom.xml").writeText("<parent>3.4.1</parent>\n")
        git("commit", "--quiet", "-am", "Update spring boot")
        host.pr = pr(git("rev-parse", "HEAD").trim(), base)
        event = dir.resolve("event.json")
        event.writeText("{}")
    }

    @Test
    fun `a PR the update broke gets the fixes pushed and the comment, and exits with 0 once nothing is left`() {
        host.pr = host.pr.copy(body = table(PARENT, "3.3.5", "3.4.1") + "\n| org.example:other | `1.0` → `2.0` |")

        val result = command(llm = ScriptedLlm(fix)).test("$repo --event $event $bot", envvars = token)

        assertEquals(0, result.statusCode, result.stderr)
        assertContains(result.stdout, "### lector-ai\n\nThe update of 2 packages broke the build. lector-ai pushed 1 commit;")
        assertEquals(listOf("Call fixed() instead of broken()"), host.pushed)
        assertEquals(listOf("ai-assisted"), host.labels)
        assertEquals("lector-ai[bot]", git("log", "-1", "--format=%an").trim())
    }

    @Test
    fun `what is left after the fix loop exits with 1`() {
        host.pr = host.pr.copy(body = table(PARENT, "3.3.5", "3.4.1"))
        val miss = Edit("App.java", "nowhere", "x")

        val result = command(llm = ScriptedLlm(miss, miss)).test("$repo --event $event $bot", envvars = token)

        assertEquals(1, result.statusCode, result.stderr)
        assertContains(result.stdout, "**1 problem caused by the update is left to fix.**")
        assertEquals("", result.stderr)
    }

    @Test
    fun `a PR that isn't the bot's is skipped with exit 0`() {
        host.pr = host.pr.copy(author = "mallory")

        val result = command().test("$repo --event $event $bot", envvars = token)

        assertEquals("Skipped: no update source recognizes the PR by mallory on renovate/spring-boot\n", result.stdout)
        assertEquals(0, result.statusCode)
        assertEquals(emptyList<String>(), host.comments)
    }

    @Test
    fun `the bot logins can be set, comma-separated, with spaces around them`() {
        host.pr = host.pr.copy(author = "my-renovate[bot]")

        val result = command().test("$repo --event $event $bot --bot-logins 'renovate[bot], my-renovate[bot] ,'", envvars = token)

        assertEquals("Skipped: the PR changes no version of the repository's maven packages\n", result.stdout)
        assertEquals(listOf(SourceOptions(setOf("renovate[bot]", "my-renovate[bot]"))), sourceOptions)
    }

    @Test
    fun `a Dependabot PR without its metadata is a tool error with exit 2 that names the setup step, also in the comment`() {
        host.pr = host.pr.copy(author = "dependabot[bot]", headBranch = "dependabot/maven/org.springframework.boot-3.4.1")

        val result = command().test("$repo --event $event $bot", envvars = token)

        assertEquals(2, result.statusCode)
        assertContains(result.stderr, "Error: The Dependabot PR comes without its metadata.")
        assertContains(result.stderr, "dependabot-metadata input, as the setup guide shows (docs/setup.md, section 3).")
        assertContains(host.comments.single(), "lector-ai can't tell what the PR changes, so it changed nothing: The Dependabot PR")
    }

    @Test
    fun `the Dependabot metadata comes from the environment or the option, and an empty value means none`() {
        host.pr = host.pr.copy(author = "mallory")
        val metadata =
            """[{"dependencyName": "$PARENT", "packageEcosystem": "maven", "prevVersion": "3.3.5", "newVersion": "3.4.1"}]"""

        command().test("$repo --event $event $bot", envvars = token + mapOf("LECTOR_DEPENDABOT_METADATA" to metadata))
        command().test(
            listOf(repo.toString(), "--event", event.toString()) + bot.split(" ") + listOf("--dependabot-metadata", metadata),
            envvars = token,
        )
        command().test("$repo --event $event $bot", envvars = token + mapOf("LECTOR_DEPENDABOT_METADATA" to ""))

        val dependency = DependabotDependency(PARENT, "maven", "3.3.5", "3.4.1")
        assertEquals(listOf(listOf(dependency), listOf(dependency), null), sourceOptions.map { it.dependabotMetadata })
    }

    @Test
    fun `the bot logins default to Renovate's and Dependabot's`() {
        host.pr = host.pr.copy(author = "mallory")

        command().test("$repo --event $event $bot", envvars = token)

        assertEquals(setOf("renovate[bot]", "dependabot[bot]"), sourceOptions.single().botLogins)
    }

    @Test
    fun `a named source must recognize the PR, and an empty name means none`() {
        host.pr = host.pr.copy(body = table(PARENT, "3.3.5", "3.4.1"))

        val named = command(llm = ScriptedLlm(fix)).test("$repo --event $event $bot --source renovate", envvars = token)

        assertEquals(0, named.statusCode, named.stderr)
        assertEquals(listOf("Call fixed() instead of broken()"), host.pushed)

        host.pr = host.pr.copy(author = "mallory")
        val empty =
            command().test(
                listOf(repo.toString(), "--event", event.toString()) + bot.split(" ") + listOf("--source", ""),
                envvars = token,
            )

        assertEquals("Skipped: no update source recognizes the PR by mallory on renovate/spring-boot\n", empty.stdout)
    }

    @Test
    fun `a green PR stays as it is and exits with 0`() {
        repo.resolve("App.java").writeText("class App {}\n")
        git("commit", "--quiet", "-am", "green")
        host.pr = host.pr.copy(headSha = git("rev-parse", "HEAD").trim(), body = table(PARENT, "3.3.5", "3.4.1"))

        val result = command().test("$repo --event $event $bot", envvars = token)

        assertContains(
            result.stdout,
            "Verify is green: nothing caused by the update. lector-ai changed nothing.\n\nCaused by the update (0)",
        )
        assertEquals(0, result.statusCode)
        assertEquals(emptyList<String>(), host.pushed)
    }

    @Test
    fun `a rejected push exits with 0 and says why`() {
        host.pr = host.pr.copy(body = table(PARENT, "3.3.5", "3.4.1"))
        host.acceptsPush = false

        val result = command(llm = ScriptedLlm(fix)).test("$repo --event $event $bot", envvars = token)

        assertEquals("The push was rejected: the branch moved on. The new head gets its own run.\n", result.stdout)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `the host gets the event, the GitHub URLs, the token, and the bot's login, and the model comes from the shared options`() {
        host.pr = host.pr.copy(author = "mallory")
        val env = token + mapOf("GITHUB_EVENT_PATH" to event.toString(), "GITHUB_API_URL" to "https://ghe.example.com/api/v3")

        command().test(
            "$repo $bot --github-server-url https://ghe.example.com --model qwen3-coder:30b --ollama-url http://ollama:11434",
            envvars = env,
        )

        assertEquals(
            listOf(
                listOf<Any>(event, URI("https://ghe.example.com/api/v3"), URI("https://ghe.example.com"), "ghs_token", "lector-ai[bot]"),
            ),
            hosts,
        )
        assertEquals(listOf(URI("http://ollama:11434") to "qwen3-coder:30b"), clients)
    }

    @Test
    fun `the GitHub URLs default to github_com`() {
        host.pr = host.pr.copy(author = "mallory")

        command().test("$repo --event $event $bot", envvars = token)

        assertEquals(listOf<Any>(URI("https://api.github.com"), URI("https://github.com")), hosts.single().subList(1, 3))
    }

    @Test
    fun `an empty Maven settings value means none, and a file is passed on`() {
        host.pr = host.pr.copy(author = "mallory")
        val file = dir.resolve("settings.xml")
        file.writeText("<settings/>")

        command().test(
            listOf(repo.toString(), "--event", event.toString()) + bot.split(" ") + listOf("--maven-settings", ""),
            envvars = token,
        )
        command().test("$repo --event $event $bot --maven-settings $file", envvars = token)
        command().test("$repo --event $event $bot", envvars = token + mapOf("LECTOR_MAVEN_SETTINGS" to ""))
        command().test("$repo --event $event $bot", envvars = token + mapOf("LECTOR_MAVEN_SETTINGS" to file.toString()))

        assertEquals(listOf(null, file, null, file), settings)
    }

    @Test
    fun `a Maven settings file that doesn't exist is a tool error with exit 2`() {
        val result = command().test("$repo --event $event $bot --maven-settings ${dir.resolve("none.xml")}", envvars = token)

        assertContains(result.stderr, "Error: Maven settings ${dir.resolve("none.xml")} is not a file")
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `without the token the run is a tool error with exit 2, before anything runs`() {
        val result = command().test("$repo --event $event $bot")

        assertEquals("", result.stdout)
        assertEquals("Error: Set LECTOR_GITHUB_TOKEN to the GitHub App's installation token\n", result.stderr)
        assertEquals(2, result.statusCode)
        assertEquals(emptyList<List<Any>>(), hosts)
    }

    @Test
    fun `a repository that isn't a directory is a tool error with exit 2`() {
        val result = command().test("${dir.resolve("none")} --event $event $bot", envvars = token)

        assertContains(result.stderr, "Error: ${dir.resolve("none")} is not a directory")
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `an unexpected failure is a tool error with exit 2`() {
        host.failure = IllegalStateException("broken host")

        val result = command().test("$repo --event $event $bot", envvars = token)

        assertContains(result.stderr, "Error: Unexpected failure: java.lang.IllegalStateException: broken host")
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `usage errors exit with 2`() {
        listOf(
            "$repo --event $event" to "missing option --bot-name",
            "$repo --event $event $bot --github-api-url ftp://api" to
                "invalid value for --github-api-url: not an http or https URL with a host",
            "$repo --event $event --bot-name ' ' --bot-email x" to "invalid value for --bot-name: must not be blank",
            "$repo $bot" to "missing option --event",
            "$repo --event ${repo.resolve("missing.json")} $bot" to
                "invalid value for --event: file \"${repo.resolve("missing.json")}\" does not exist",
            "$repo --event $repo $bot" to "invalid value for --event: file \"$repo\" is a directory",
            "$repo --event $event $bot --bot-logins ' , '" to "invalid value for --bot-logins: must name at least one bot",
            "$repo --event $event $bot --source snyk" to "invalid value for --source: must be one of renovate, dependabot, fallback",
            "$repo --event $event $bot --dependabot-metadata {}" to
                "invalid value for --dependabot-metadata: The Dependabot metadata is not fetch-metadata's updated-dependencies-json: not a JSON array",
        ).forEach { (args, error) ->
            val run = LectorCommand("1.2.3").subcommands(command()).executeInProcess("pr", *split(args))

            assertContains(run.stderr, error, message = args)
            assertEquals(2, run.exitCode, args)
        }
    }

    private fun split(args: String) =
        Regex("""'[^']*'|\S+""")
            .findAll(args)
            .map { it.value.trim('\'') }
            .toList()
            .toTypedArray()

    private fun table(
        name: String,
        from: String,
        to: String,
    ) = "This PR contains the following updates:\n\n| Package | Change |\n|---|---|\n| $name | `$from` → `$to` |"

    private fun pr(
        head: String,
        base: String,
    ) = PullRequest(3, "renovate[bot]", "renovate/spring-boot", head, "acme/app", "acme/app", base, "")

    private fun command(
        llm: LlmClient = ScriptedLlm(),
        index: RecipeIndex = RecipeIndex { _, _ -> RecipeSelection.NONE },
    ) = PrCommand(
        adapters = { mavenSettings ->
            settings += mavenSettings
            listOf(FakeAdapter())
        },
        sources = { options ->
            sourceOptions += options
            sources(options)
        },
        index = { index },
        llm = { url, model ->
            clients += url to model
            llm
        },
        host = { event, apiUrl, serverUrl, token, botLogin ->
            hosts += listOf(event, apiUrl, serverUrl, token, botLogin)
            host
        },
    )

    private fun git(vararg args: String): String {
        val command = listOf("git", "-c", "user.name=Renovate", "-c", "user.email=bot@renovateapp.com", "-c", "commit.gpgsign=false")
        val result = runProcess(command + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }

    /** A host that hands out [pr] and records what the run did; a push records the subjects it would push. */
    private inner class FakeHost : GitHost {
        lateinit var pr: PullRequest
        var acceptsPush = true
        var failure: RuntimeException? = null
        val pushed = mutableListOf<String>()
        val comments = mutableListOf<String>()
        val labels = mutableListOf<String>()

        override fun pullRequest(): PullRequest {
            failure?.let { throw it }
            return pr
        }

        override fun fetch(
            repo: Path,
            pr: PullRequest,
            commit: String,
        ) = error("the base commit is there")

        override fun push(
            repo: Path,
            pr: PullRequest,
        ): Boolean {
            pushed += git("log", "--reverse", "--format=%s", "${pr.headSha}..HEAD").lines().filter { it.isNotEmpty() }
            return acceptsPush
        }

        override fun comment(
            pr: PullRequest,
            body: String,
            create: Boolean,
        ) {
            comments += body
        }

        override fun label(
            pr: PullRequest,
            label: String,
        ) {
            labels += label
        }
    }
}
