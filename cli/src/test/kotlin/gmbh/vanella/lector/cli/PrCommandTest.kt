package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import gmbh.vanella.lector.core.Edit
import gmbh.vanella.lector.core.GitHost
import gmbh.vanella.lector.core.LlmClient
import gmbh.vanella.lector.core.PrState
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
import kotlin.test.assertFalse
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
    private val inits = mutableListOf<Path?>()
    private val registries = mutableListOf<Pair<Path?, Path?>>()
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
        assertContains(result.stdout, "\n\nAll 2 changes:\n- $PARENT 3.3.5 → 3.4.1\n- org.example:other 1.0 → 2.0\n")
        assertEquals(listOf("Call fixed() instead of broken()"), host.pushed)
        assertEquals(listOf("ai-assisted"), host.labels)
        assertEquals("lector-ai[bot]", git("log", "-1", "--format=%an").trim())
    }

    @Test
    fun `what is left after the fix loop exits with 1`() {
        host.pr = host.pr.copy(body = table(PARENT, "3.3.5", "3.4.1"))
        val miss = Edit("App.java", "nowhere", "x")

        val result = command(llm = ScriptedLlm(miss, miss.copy(replace = "y"))).test("$repo --event $event $bot", envvars = token)

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
    fun `a PR that only updates the Gradle wrapper is skipped with exit 0`() {
        host.pr = host.pr.copy(body = table("[gradle](https://gradle.org)", "9.2.1", "9.5.1"))

        val result = command().test("$repo --event $event $bot", envvars = token)

        assertEquals("Skipped: the PR changes no version of the repository's maven packages\n", result.stdout)
        assertEquals(0, result.statusCode)
        assertEquals(emptyList<String>(), host.comments)
    }

    @Test
    fun `a PR that only updates Node, in nvm or the setup-node action, is skipped with exit 0`() {
        host.pr =
            host.pr.copy(
                body =
                    "This PR contains the following updates:\n\n| Package | Type | Update | Change |\n|---|---|---|---|\n" +
                        "| [node](https://redirect.github.com/actions/node-versions) | uses-with | major | `22` → `24` |\n" +
                        "| [node](https://nodejs.org) ([source](https://redirect.github.com/nodejs/node)) |  | major | `22` → `24` |\n",
            )

        val result = command().test("$repo --event $event $bot", envvars = token)

        assertEquals("Skipped: the PR changes no version of the repository's maven packages\n", result.stdout)
        assertEquals("", result.stderr)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `the bot logins can be set, comma-separated, with spaces around them`() {
        host.pr = host.pr.copy(author = "my-renovate[bot]")

        val result = command().test("$repo --event $event $bot --bot-logins 'renovate[bot], my-renovate[bot] ,'", envvars = token)

        assertEquals("Skipped: the PR changes no version of the repository's maven packages\n", result.stdout)
        assertEquals(listOf(setOf("renovate[bot]", "my-renovate[bot]")), sourceOptions.map { it.botLogins })
    }

    @Test
    fun `a Dependabot PR without its metadata is a tool error with exit 2 that names the setup step, also in the comment`() {
        host.pr = host.pr.copy(author = "dependabot[bot]", headBranch = "dependabot/maven/org.springframework.boot-3.4.1")

        val result = command().test("$repo --event $event $bot", envvars = token)

        assertEquals(2, result.statusCode)
        assertContains(result.stderr, "Error: The Dependabot PR comes without its metadata.")
        assertContains(result.stderr, "dependabot-metadata input, as the setup guide shows (docs/setup.md, \"Advanced setup\").")
        assertContains(host.comments.single(), "lector-ai can't tell what the PR changes, so it changed nothing: The Dependabot PR")
    }

    @Test
    fun `a Dependabot PR gets its fixes in a companion PR by default, and in place with the mode in-place`() {
        val metadata =
            """[{"dependencyName": "$PARENT", "packageEcosystem": "maven", "prevVersion": "3.3.5", "newVersion": "3.4.1"}]"""
        host.pr = host.pr.copy(author = "dependabot[bot]", headBranch = "dependabot/maven/boot")
        val env = token + mapOf("LECTOR_DEPENDABOT_METADATA" to metadata)

        val companion = command(llm = ScriptedLlm(fix)).test("$repo --event $event $bot", envvars = env)

        assertEquals(0, companion.statusCode, companion.stderr)
        assertContains(companion.stdout, "lector-ai pushed 1 commit to the companion PR #20;")
        assertEquals(listOf("heal/dependabot/maven/boot" to listOf("Call fixed() instead of broken()")), host.forcePushed)
        assertEquals(listOf("heal/dependabot/maven/boot -> dependabot/maven/boot"), host.created)
        assertEquals(emptyList<String>(), host.pushed)

        git("reset", "--hard", "--quiet", host.pr.headSha)
        val inPlace = command(llm = ScriptedLlm(fix)).test("$repo --event $event $bot --mode in-place", envvars = env)

        assertEquals(0, inPlace.statusCode, inPlace.stderr)
        assertEquals(listOf("Call fixed() instead of broken()"), host.pushed)
    }

    @Test
    fun `a Renovate PR goes into a companion PR with the mode companion, and an empty mode or source means none`() {
        host.pr = host.pr.copy(body = table(PARENT, "3.3.5", "3.4.1"))

        command(llm = ScriptedLlm(fix)).test("$repo --event $event $bot --mode companion", envvars = token)

        assertEquals(listOf("heal/renovate/spring-boot -> renovate/spring-boot"), host.created)

        git("reset", "--hard", "--quiet", host.pr.headSha)
        // As the action passes them: attached, so that an empty value isn't lost.
        command(llm = ScriptedLlm(fix)).test("$repo --event $event $bot --mode= --source=", envvars = token)

        assertEquals(listOf("Call fixed() instead of broken()"), host.pushed)
    }

    @Test
    fun `a closed PR gets its companion PR closed and exits with 0`() {
        host.pr = host.pr.copy(state = PrState.CLOSED)
        host.companion = 21

        val result = command().test("$repo --event $event $bot", envvars = token)

        assertEquals("The PR is closed: closed the companion PR #21.\n", result.stdout)
        assertEquals(listOf<Any>(21, "heal/renovate/spring-boot"), host.closed)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `a green PR gets its companion PR closed`() {
        repo.resolve("App.java").writeText("class App {}\n")
        git("commit", "--quiet", "-am", "green")
        host.pr = host.pr.copy(headSha = git("rev-parse", "HEAD").trim(), body = table(PARENT, "3.3.5", "3.4.1"))
        host.companion = 21

        val result = command().test("$repo --event $event $bot", envvars = token)

        assertContains(
            result.stdout,
            "Verify is green: nothing caused by the update. lector-ai changed nothing. Closed the companion PR #21.\n",
        )
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `a closed PR without a companion PR says so and exits with 0`() {
        host.pr = host.pr.copy(state = PrState.MERGED)

        val result = command().test("$repo --event $event $bot", envvars = token)

        assertEquals("The PR is merged. It has no companion PR.\n", result.stdout)
        assertEquals(0, result.statusCode)
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
    fun `a labelled PR gets its changes from the resolved versions of base and head, read in a worktree removed afterwards`() {
        host.pr = host.pr.copy(author = "alice", headBranch = "bump-boot", labels = setOf("lector"))

        val result = command(llm = ScriptedLlm(fix)).test("$repo --event $event $bot", envvars = token)

        assertEquals(0, result.statusCode, result.stderr)
        assertContains(result.stdout, "The update of $PARENT 3.3.5 → 3.4.1 broke the build.")
        assertFalse("All 1 changes" in result.stdout)
        assertEquals(listOf("heal/bump-boot" to listOf("Call fixed() instead of broken()")), host.forcePushed)
        assertEquals(1, git("worktree", "list").lines().count { it.isNotEmpty() })
    }

    @Test
    fun `the opt-in label can be set`() {
        host.pr = host.pr.copy(author = "alice", headBranch = "bump-boot", labels = setOf("upgrade"))

        val result = command(llm = ScriptedLlm(fix)).test("$repo --event $event $bot --fallback-label upgrade", envvars = token)

        assertEquals(listOf("upgrade"), sourceOptions.map { it.fallbackLabel })
        assertEquals(listOf("heal/bump-boot -> bump-boot"), host.created, result.stderr)
    }

    @Test
    fun `a labelled PR whose resolved versions can't be read is a tool error with exit 2, and the comment says why`() {
        host.pr = host.pr.copy(author = "alice", headBranch = "bump-boot", labels = setOf("lector"))

        val result = command(adapter = FakeAdapter(versionsReadable = false)).test("$repo --event $event $bot", envvars = token)

        val reason =
            "The resolved versions of the base commit can't be read, so lector-ai can't tell what the PR changes: " +
                "mvn could not resolve the parent POM"
        assertEquals(2, result.statusCode)
        assertContains(result.stderr, "Error: $reason")
        assertEquals(listOf("### lector-ai\n\nlector-ai can't tell what the PR changes, so it changed nothing: $reason"), host.comments)
        assertEquals(emptyList<String>(), host.pushed)
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
    fun `an empty Gradle init script value means none, and a file is passed on`() {
        host.pr = host.pr.copy(author = "mallory")
        val file = dir.resolve("mirror.gradle")
        file.writeText("")

        command().test(
            listOf(repo.toString(), "--event", event.toString()) + bot.split(" ") + listOf("--gradle-init", ""),
            envvars = token,
        )
        command().test("$repo --event $event $bot --gradle-init $file", envvars = token)
        command().test("$repo --event $event $bot", envvars = token + mapOf("LECTOR_GRADLE_INIT" to ""))
        command().test("$repo --event $event $bot", envvars = token + mapOf("LECTOR_GRADLE_INIT" to file.toString()))

        assertEquals(listOf(null, file, null, file), inits)
    }

    @Test
    fun `an empty npmrc or yarnrc means none, and a file is passed on`() {
        host.pr = host.pr.copy(author = "mallory")
        val npmrc = dir.resolve("ci.npmrc").also { it.writeText("") }
        val yarnrc = dir.resolve("ci.yarnrc.yml").also { it.writeText("") }

        val results =
            listOf(
                command().test(
                    listOf(repo.toString(), "--event", event.toString()) + bot.split(" ") + listOf("--npmrc", "", "--yarnrc", ""),
                    envvars = token,
                ),
                command().test("$repo --event $event $bot --npmrc $npmrc --yarnrc $yarnrc", envvars = token),
                command().test(
                    "$repo --event $event $bot",
                    envvars =
                        token + mapOf("LECTOR_NPMRC" to npmrc.toString(), "LECTOR_YARNRC" to yarnrc.toString()),
                ),
            )

        assertEquals(listOf(null to null, npmrc to yarnrc, npmrc to yarnrc), registries)
        for (result in results) {
            assertEquals(0, result.statusCode, result.stderr)
            assertEquals("Skipped: no update source recognizes the PR by mallory on renovate/spring-boot\n", result.stdout)
            assertEquals("", result.stderr)
        }
    }

    @Test
    fun `an npmrc or yarnrc that doesn't exist is a tool error with exit 2`() {
        for ((option, name) in listOf("--npmrc" to "npmrc", "--yarnrc" to "yarnrc")) {
            val result = command().test("$repo --event $event $bot $option ${dir.resolve("none")}", envvars = token)

            assertContains(result.stderr, "Error: The $name ${dir.resolve("none")} is not a file")
            assertEquals("", result.stdout)
            assertEquals(2, result.statusCode)
        }
    }

    @Test
    fun `a Gradle init script that doesn't exist is a tool error with exit 2`() {
        val result = command().test("$repo --event $event $bot --gradle-init ${dir.resolve("none.gradle")}", envvars = token)

        assertContains(result.stderr, "Error: Gradle init script ${dir.resolve("none.gradle")} is not a file")
        assertEquals(2, result.statusCode)
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
            "$repo --event $event --app-slug my-lector --github-server-url http://github.com" to
                "--app-slug works on github.com only, where the bot's email is known. On http://github.com, name the bot",
            "$repo --event $event --app-slug ' '" to "invalid value for --app-slug: must not be blank",
            "$repo --event $event $bot --bot-logins ' , '" to "invalid value for --bot-logins: must name at least one bot",
            "$repo --event $event $bot --fallback-label ' '" to "invalid value for --fallback-label: must not be blank",
            "$repo --event $event $bot --mode force" to "invalid value for --mode: must be in-place or companion",
            "$repo --event $event $bot --source snyk" to "invalid value for --source: must be one of renovate, dependabot, fallback",
            "$repo --event $event $bot --dependabot-metadata {}" to
                "invalid value for --dependabot-metadata: The Dependabot metadata is not fetch-metadata's updated-dependencies-json: not a JSON array",
        ).forEach { (args, error) ->
            val run = LectorCommand("1.2.3").subcommands(command()).executeInProcess("pr", *split(args))

            assertContains(run.stderr, error, message = args)
            assertEquals(2, run.exitCode, args)
        }
    }

    @Test
    fun `a bot named the wrong way prints the usage of pr, like every other usage error of pr`() {
        listOf(
            "$repo --event $event",
            "$repo --event $event --app-slug my-lector --bot-name lector-ai[bot]",
            "$repo --event $event --app-slug my-lector --github-server-url https://ghe.example.com",
        ).forEach { args ->
            val run = LectorCommand("1.2.3").subcommands(command()).executeInProcess("pr", *split(args))

            assertContains(run.stderr, "Usage: lector pr [<options>] [<repo>]", message = args)
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
        adapter: FakeAdapter = FakeAdapter(),
    ) = PrCommand(
        adapters = { config ->
            settings += config.mavenSettings
            inits += config.gradleInit
            registries += config.npmrc to config.yarnrc
            listOf(adapter)
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
        val forcePushed = mutableListOf<Pair<String, List<String>>>()
        val created = mutableListOf<String>()
        var companion: Int? = null
        val closed = mutableListOf<Any>()

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
            number: Int,
            label: String,
        ) {
            labels += label
        }

        override fun forcePush(
            repo: Path,
            branch: String,
        ) {
            forcePushed += branch to git("log", "--reverse", "--format=%s", "${pr.headSha}..HEAD").lines().filter { it.isNotEmpty() }
        }

        override fun findPullRequest(
            head: String,
            base: String,
        ): Int? = companion

        override fun createPullRequest(
            head: String,
            base: String,
            title: String,
            body: String,
        ): Int {
            created += "$head -> $base"
            return 20
        }

        override fun updatePullRequest(
            number: Int,
            body: String,
        ) = error("no companion PR to update")

        override fun closePullRequest(number: Int) {
            closed += number
        }

        override fun deleteBranch(branch: String) {
            closed += branch
        }
    }
}
