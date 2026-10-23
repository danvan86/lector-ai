package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import com.sun.net.httpserver.HttpServer
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.RecipeSelection
import gmbh.vanella.lector.core.runProcess
import gmbh.vanella.lector.ecosystems.jvm.MavenAdapter
import gmbh.vanella.lector.git.GitHubHost
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

/**
 * M10: `lector pr --app-slug` takes the bot's identity from GitHub instead of the typed-in name and email, and works
 * exactly like the pair afterwards. Real sources, GitHub host, and Maven adapter; GitHub's API and Ollama are local HTTP
 * servers, the remote is a local bare repository, and the project's Maven wrapper stands in for Maven.
 */
class AppSlugFlowTest {
    @TempDir
    lateinit var dir: Path

    private val origin by lazy { dir.resolve("server/acme/app.git") }
    private val repo by lazy { dir.resolve("workspace") }
    private val github = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val ollama = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    /** Every request to GitHub: method, decoded path, and the Authorization header. */
    private val requests = mutableListOf<String>()

    /** GitHub's answer to the user lookup. */
    private var user = 200 to """{"login": "my-lector[bot]", "id": 123, "type": "Bot"}"""

    /** GitHub's answer to the list of the PR's comments. */
    private var comments = "[]"

    init {
        github.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests += "${exchange.requestMethod} $path ${exchange.requestHeaders.getFirst("Authorization")}"
            val (status, answer) =
                when {
                    path.startsWith("/users/") -> user
                    exchange.requestMethod == "GET" && path.endsWith("/comments") -> 200 to comments
                    exchange.requestMethod == "GET" -> 200 to "[]"
                    path == "/repos/acme/app/pulls" -> 201 to """{"number": 21}"""
                    exchange.requestMethod == "POST" -> 201 to "{}"
                    else -> 200 to "{}"
                }
            val bytes = answer.encodeToByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        ollama.createContext("/") { exchange ->
            exchange.requestBody.readAllBytes()
            val edit = """{"file":"src/main/java/App.java","search":"broken();","replace":"fixed();"}"""
            val content = """{"analysis":"renamed","rationale":"Call fixed() instead of broken()","edits":[$edit]}"""
            val answer = buildJsonObject { put("message", buildJsonObject { put("content", content) }) }.toString().encodeToByteArray()
            exchange.sendResponseHeaders(200, answer.size.toLong())
            exchange.responseBody.use { it.write(answer) }
        }
        github.start()
        ollama.start()
    }

    @AfterTest
    fun `stop the servers`() {
        github.stop(0)
        ollama.stop(0)
    }

    // Group A · The identity from the App's slug

    @Test
    fun `a broken Renovate PR healed with the App's slug is pushed under the bot's name and noreply email`() {
        val event = renovatePr(broken = true)

        val result = run("$repo --event $event --app-slug my-lector")

        assertEquals(0, result.statusCode, result.stdout + result.stderr)
        assertContains(result.stdout, "lector-ai pushed 1 commit; nothing caused by the update is left.")
        assertEquals(
            "my-lector[bot] <123+my-lector[bot]@users.noreply.github.com>|Call fixed() instead of broken()",
            git(origin, "log", "-1", "--format=%an <%ae>|%s", "renovate/spring-boot"),
            "the fix commit is the bot's, with the email GitHub links to the App",
        )
    }

    @Test
    fun `the bot is looked up with the token before anything else is asked of GitHub`() {
        val event = renovatePr(broken = true)

        run("$repo --event $event --app-slug my-lector")

        assertEquals("GET /users/my-lector[bot] Bearer ghs_token", requests.firstOrNull(), "the first request is the lookup")
        assertEquals(1, requests.count { it.startsWith("GET /users/") }, "one lookup per run")
    }

    @Test
    fun `the report comment the bot wrote earlier is updated, not written a second time`() {
        val event = renovatePr(broken = false)
        comments = """[{"id": 5, "user": {"login": "my-lector[bot]"}, "body": "<!-- lector-ai -->\nThe old report"}]"""

        val result = run("$repo --event $event --app-slug my-lector")

        assertEquals(0, result.statusCode, result.stdout + result.stderr)
        assertContains(requests.map { it.substringBeforeLast(" ") }, "PATCH /repos/acme/app/issues/comments/5")
        assertFalse(requests.any { it.startsWith("POST /repos/acme/app/issues/7/comments") }, "no second report: $requests")
    }

    @Test
    fun `a head commit with the bot's derived email is the tool's own and is skipped without a build`() {
        val event = renovatePr(broken = true, headEmail = "123+my-lector[bot]@users.noreply.github.com")

        val result = run("$repo --event $event --app-slug my-lector")

        assertEquals("Skipped: the head commit is the tool's own\n", result.stdout)
        assertEquals(0, result.statusCode, result.stderr)
        assertFalse(repo.resolve("mvn.log").exists(), "Maven never ran")
    }

    @Test
    fun `a server URL with a trailing slash is still github_com`() {
        val event = renovatePr(broken = false)

        val result = run("$repo --event $event --app-slug my-lector --github-server-url https://github.com/")

        assertEquals(0, result.statusCode, result.stdout + result.stderr)
        assertEquals("GET /users/my-lector[bot] Bearer ghs_token", requests.firstOrNull(), "the first request is the lookup")
    }

    @Test
    fun `a GitHub other than github_com refuses the App's slug before any request, and names the options to use instead`() {
        val event = renovatePr(broken = false)

        val result =
            asThePipelineCallsIt(
                listOf(
                    repo.toString(),
                    "--event",
                    event.toString(),
                    "--app-slug",
                    "my-lector",
                    "--github-server-url",
                    "https://ghe.example.com",
                ) +
                    api(),
            )

        assertEquals(2, result.exitCode, result.stderr)
        assertEquals("", result.stdout)
        assertContains(result.stderr, "github.com", message = "the reason: the bot's email is only known on github.com")
        assertContains(result.stderr, "--bot-name")
        assertContains(result.stderr, "--bot-email")
        assertEquals(emptyList(), requests)
    }

    @Test
    fun `an answer that doesn't name the App's bot is a tool error with exit 2 before any build`() {
        val event = renovatePr(broken = true)
        val answers =
            listOf(
                "no such user" to (404 to """{"message": "Not Found"}"""),
                "a person, not a bot" to (200 to """{"login": "my-lector[bot]", "id": 123, "type": "User"}"""),
                "another login" to (200 to """{"login": "other[bot]", "id": 123, "type": "Bot"}"""),
                "no id" to (200 to """{"login": "my-lector[bot]", "type": "Bot"}"""),
                "id 0" to (200 to """{"login": "my-lector[bot]", "id": 0, "type": "Bot"}"""),
                "id not a number" to (200 to """{"login": "my-lector[bot]", "id": "abc", "type": "Bot"}"""),
                "not JSON" to (200 to "<html>"),
                "a server error" to (500 to """{"message": "Server Error"}"""),
            )

        for ((case, answer) in answers) {
            requests.clear()
            user = answer

            val result = run("$repo --event $event --app-slug my-lector")

            assertEquals(2, result.statusCode, case)
            assertEquals("", result.stdout, case)
            assertContains(result.stderr, "Error: ", message = case)
            assertContains(result.stderr, "my-lector", message = case)
            assertEquals(listOf("GET /users/my-lector[bot] Bearer ghs_token"), requests, case)
            assertFalse(repo.resolve("mvn.log").exists(), "$case: Maven never ran")
        }
    }

    @Test
    fun `an unreachable GitHub during the lookup is a tool error with exit 2 that names the slug`() {
        val event = renovatePr(broken = true)
        val closed = ServerSocket(0).use { it.localPort }

        val result =
            command().test(
                "$repo --event $event --app-slug my-lector --github-api-url http://127.0.0.1:$closed",
                envvars = TOKEN,
            )

        assertEquals(2, result.statusCode)
        assertEquals("", result.stdout)
        assertContains(result.stderr, "my-lector")
        assertFalse(repo.resolve("mvn.log").exists(), "Maven never ran")
    }

    // Group B · One way to name the bot

    @Test
    fun `naming the bot in no way, in both ways, or with half the pair is a usage error with exit 2 that names both ways`() {
        val event = renovatePr(broken = false)
        val pair = listOf("--bot-name", "lector-ai[bot]", "--bot-email", "1+lector-ai[bot]@users.noreply.github.com")
        val cases =
            listOf(
                "neither" to emptyList(),
                "slug and the pair" to listOf("--app-slug", "my-lector") + pair,
                "slug and the name" to listOf("--app-slug", "my-lector", "--bot-name", "lector-ai[bot]"),
                "slug and the email" to listOf("--app-slug", "my-lector", "--bot-email", "1+lector-ai[bot]@users.noreply.github.com"),
                "only the name" to listOf("--bot-name", "lector-ai[bot]"),
                "only the email" to listOf("--bot-email", "1+lector-ai[bot]@users.noreply.github.com"),
                "all empty, as the action passes them" to listOf("--app-slug=", "--bot-name=", "--bot-email="),
            )

        for ((case, identity) in cases) {
            val result = asThePipelineCallsIt(listOf(repo.toString(), "--event", event.toString()) + api() + identity)

            assertEquals(2, result.exitCode, "$case: ${result.stderr}")
            assertEquals("", result.stdout, case)
            for (option in listOf("--app-slug", "--bot-name", "--bot-email")) {
                assertContains(result.stderr, option, message = "$case: ${result.stderr}")
            }
            assertEquals(emptyList(), requests, case)
        }
    }

    @Test
    fun `the action's empty values count as not given, so the slug alone or the pair alone is used`() {
        val event = renovatePr(broken = false)

        val slug =
            command().test(
                listOf(
                    repo.toString(),
                    "--event",
                    event.toString(),
                ) + api() + listOf("--app-slug=my-lector", "--bot-name=", "--bot-email="),
                envvars = TOKEN,
            )

        assertEquals(0, slug.statusCode, slug.stdout + slug.stderr)
        assertEquals("GET /users/my-lector[bot] Bearer ghs_token", requests.firstOrNull(), "the first request is the lookup")

        requests.clear()
        val pair =
            command().test(
                listOf(repo.toString(), "--event", event.toString()) + api() +
                    listOf("--app-slug=", "--bot-name=lector-ai[bot]", "--bot-email=1+lector-ai[bot]@users.noreply.github.com"),
                envvars = TOKEN,
            )

        assertEquals(0, pair.statusCode, pair.stdout + pair.stderr)
        assertFalse(requests.any { it.startsWith("GET /users/") }, "no lookup with the pair: $requests")
    }

    /**
     * Runs `lector pr` the way the pipeline does, with the exit codes of the CLI's contract (a usage error exits with 2),
     * and without the environment: whatever fails here fails before the token is read.
     */
    private fun asThePipelineCallsIt(args: List<String>) =
        LectorCommand("1.2.3").subcommands(command()).executeInProcess("pr", *args.toTypedArray())

    private fun run(args: String): CliktCommandTestResult = command().test("$args ${api().joinToString(" ")}", envvars = TOKEN)

    private fun api() =
        listOf(
            "--github-api-url",
            "http://127.0.0.1:${github.address.port}",
            "--ollama-url",
            "http://127.0.0.1:${ollama.address.port}",
        )

    private fun command() =
        PrCommand(
            adapters = { listOf(MavenAdapter(it.mavenSettings)) },
            sources = ::sources,
            index = { RecipeIndex { _, _ -> RecipeSelection.NONE } },
            // The CLI only takes http(s) servers; the test's remote is a local directory.
            host = { event, apiUrl, _, token, botLogin -> GitHubHost(event, apiUrl, dir.resolve("server").toUri(), token, botLogin) },
        )

    /**
     * Renovate's PR 7 on the origin, cloned into the workspace at its head. The Maven wrapper reports a compile error for
     * `broken();` in `App.java` once the parent is 3.4.1 and writes the effective POM when asked. A [broken] PR still
     * calls `broken()`, a green one `fixed()`. The head commit's author email is [headEmail].
     */
    private fun renovatePr(
        broken: Boolean,
        headEmail: String = "bot@renovateapp.com",
    ): Path {
        val work = dir.resolve("origin-work")
        work.resolve("src/main/java").createDirectories()
        val call = if (broken) "broken();" else "fixed();"
        work.resolve("src/main/java/App.java").writeText("class App {\n    void run() {\n        $call\n    }\n}\n")
        work.resolve("pom.xml").writeText("<project><parent><version>3.3.5</version></parent></project>\n")
        work.resolve(".gitignore").writeText("mvn.log\ntarget/\n")
        val wrapper = work.resolve("mvnw")
        wrapper.writeText(
            """
            #!/bin/sh
            root="${'$'}(dirname "${'$'}0")"
            echo "${'$'}*" >> "${'$'}root/mvn.log"
            for arg in "${'$'}@"; do
              case "${'$'}arg" in
                -Doutput=*) echo "<project><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>${'$'}(grep -o '3\.[0-9.]*' "${'$'}root/pom.xml")</version></parent></project>" > "${'$'}{arg#-Doutput=}"; exit 0 ;;
              esac
            done
            if grep -q 'broken();' "${'$'}root/src/main/java/App.java" && grep -q '3.4.1' "${'$'}root/pom.xml"; then
              echo "[ERROR] ${'$'}root/src/main/java/App.java:[3,9] cannot find symbol"
              exit 1
            fi
            exit 0
            """.trimIndent() + "\n",
        )
        wrapper.toFile().setExecutable(true)
        git(work, "init", "--quiet", "--initial-branch", "main")
        git(work, "add", ".")
        git(work, "-c", "user.name=Dev", "-c", "user.email=dev@example.com", "commit", "--quiet", "-m", "base")
        val base = git(work, "rev-parse", "HEAD")
        git(work, "switch", "--quiet", "-c", "renovate/spring-boot")
        work.resolve("pom.xml").writeText("<project><parent><version>3.4.1</version></parent></project>\n")
        git(work, "-c", "user.name=Renovate Bot", "-c", "user.email=$headEmail", "commit", "--quiet", "-am", "Update spring boot")
        val head = git(work, "rev-parse", "HEAD")
        git(dir, "init", "--quiet", "--bare", origin.toString())
        git(work, "push", "--quiet", origin.toString(), "main", "renovate/spring-boot")
        git(dir, "clone", "--quiet", "--branch", "renovate/spring-boot", origin.toUri().toString(), repo.toString())
        return event(head, base)
    }

    private fun event(
        head: String,
        base: String,
    ): Path {
        val body = "This PR contains the following updates:\n\n| Package | Change |\n|---|---|\n| $PARENT | `3.3.5` → `3.4.1` |"
        val pr =
            buildJsonObject {
                put("number", 7)
                put("state", "open")
                put("merged", false)
                put("labels", buildJsonArray {})
                put("body", body)
                put("user", buildJsonObject { put("login", "renovate[bot]") })
                put(
                    "head",
                    buildJsonObject {
                        put("ref", "renovate/spring-boot")
                        put("sha", head)
                        put("repo", buildJsonObject { put("full_name", "acme/app") })
                    },
                )
                put(
                    "base",
                    buildJsonObject {
                        put("sha", base)
                        put("repo", buildJsonObject { put("full_name", "acme/app") })
                    },
                )
            }
        val file = dir.resolve("event.json")
        file.writeText(buildJsonObject { put("pull_request", pr) }.toString())
        return file
    }

    private fun git(
        where: Path,
        vararg args: String,
    ): String {
        val result = runProcess(listOf("git", "-c", "commit.gpgsign=false") + args, where, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output.trim()
    }

    private companion object {
        val TOKEN = mapOf("LECTOR_GITHUB_TOKEN" to "ghs_token")
    }
}
