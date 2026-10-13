package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import com.sun.net.httpserver.HttpServer
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.RecipeSelection
import gmbh.vanella.lector.core.runProcess
import gmbh.vanella.lector.ecosystems.jvm.MavenAdapter
import gmbh.vanella.lector.git.GitHubHost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * A PR from red to green through the real adapters: the sources, the GitHub host, the Maven adapter, and the Ollama
 * client. Only the edges are fakes: GitHub's REST API and Ollama are local HTTP servers, the remote is a
 * local bare repository, and the project's Maven wrapper stands in for Maven.
 */
class PrFlowTest {
    @TempDir
    lateinit var dir: Path

    private val origin by lazy { dir.resolve("server/acme/app.git") }
    private val repo by lazy { dir.resolve("workspace") }
    private val github = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val ollama = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val githubRequests = mutableListOf<String>()
    private val prompts = mutableListOf<String>()

    init {
        github.createContext("/") { exchange ->
            val body = exchange.requestBody.readAllBytes().decodeToString()
            githubRequests += "${exchange.requestMethod} ${exchange.requestURI.path} $body".trim()
            val answer =
                when {
                    exchange.requestMethod == "GET" -> "[]"
                    exchange.requestURI.path == "/repos/acme/app/pulls" -> """{"number": 21}"""
                    else -> "{}"
                }
            exchange.sendResponseHeaders(if (exchange.requestMethod == "POST") 201 else 200, answer.length.toLong())
            exchange.responseBody.use { it.write(answer.encodeToByteArray()) }
        }
        ollama.createContext("/") { exchange ->
            prompts += exchange.requestBody.readAllBytes().decodeToString()
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

    @Test
    fun `a Renovate PR the update broke is fixed, pushed, commented, and labelled, with the base fetched into a shallow clone`() {
        val (base, head) = renovatePr()
        git(dir, "clone", "--quiet", "--depth", "1", "--branch", "renovate/spring-boot", origin.toUri().toString(), repo.toString())
        val event = event(head, base)

        val result =
            command().test(
                "$repo --event $event --bot-name lector-ai[bot] --bot-email 1+lector-ai[bot]@users.noreply.github.com " +
                    "--github-api-url http://127.0.0.1:${github.address.port} --ollama-url http://127.0.0.1:${ollama.address.port}",
                envvars = mapOf("LECTOR_GITHUB_TOKEN" to "ghs_token"),
            )

        assertEquals(0, result.statusCode, result.stdout + result.stderr)
        assertContains(result.stdout, "lector-ai pushed 1 commit; nothing caused by the update is left.")
        val pushed = git(origin, "log", "--format=%an|%s", "renovate/spring-boot").lines()
        assertEquals(listOf("lector-ai[bot]|Call fixed() instead of broken()", "Renovate Bot|Update spring boot"), pushed.take(2))
        assertContains(git(origin, "show", "renovate/spring-boot:src/main/java/App.java"), "fixed();")
        assertEquals(
            listOf(
                "GET /repos/acme/app/issues/7/comments",
                "POST /repos/acme/app/issues/7/comments",
                "POST /repos/acme/app/issues/7/labels",
            ),
            githubRequests.map { it.substringBefore(" {").substringBefore(" [") },
        )
        assertContains(githubRequests[1], "<!-- lector-ai -->")
        assertContains(githubRequests[2], "ai-assisted")
        val prompt = Json.parseToJsonElement(prompts.single()).jsonObject.toString()
        assertContains(prompt, "The update of $PARENT from 3.3.5 to 3.4.1 broke this code.")
        assertContains(prompt, "Renamed broken() to fixed()", message = "the release notes from the PR body")
        assertTrue(
            repo
                .resolve("mvn.log")
                .readText()
                .lines()
                .any { "verify" in it },
        )
    }

    @Test
    fun `a labelled PR is fixed in a companion PR on top of its head, labelled there, and reported on the PR`() {
        val (base, head) = renovatePr()
        git(dir, "clone", "--quiet", "--branch", "renovate/spring-boot", origin.toUri().toString(), repo.toString())
        val event = event(head, base, author = "alice", labels = listOf("lector"))

        val result =
            command().test(
                "$repo --event $event --bot-name lector-ai[bot] --bot-email 1+lector-ai[bot]@users.noreply.github.com " +
                    "--github-api-url http://127.0.0.1:${github.address.port} --ollama-url http://127.0.0.1:${ollama.address.port}",
                envvars = mapOf("LECTOR_GITHUB_TOKEN" to "ghs_token"),
            )

        assertEquals(0, result.statusCode, result.stdout + result.stderr)
        assertContains(result.stdout, "lector-ai pushed 1 commit to the companion PR #21; nothing caused by the update is left.")
        assertEquals(head, git(origin, "rev-parse", "renovate/spring-boot"), "the PR's own branch is untouched")
        val companion = git(origin, "log", "--format=%an|%s", "heal/renovate/spring-boot").lines()
        assertEquals(listOf("lector-ai[bot]|Call fixed() instead of broken()", "Renovate Bot|Update spring boot"), companion.take(2))
        assertEquals(
            listOf(
                "GET /repos/acme/app/pulls",
                "POST /repos/acme/app/pulls",
                "POST /repos/acme/app/issues/21/labels",
                "GET /repos/acme/app/issues/7/comments",
                "POST /repos/acme/app/issues/7/comments",
            ),
            githubRequests.map { it.substringBefore(" {").substringBefore(" [") },
        )
        assertContains(githubRequests[1], "\"head\":\"heal/renovate/spring-boot\",\"base\":\"renovate/spring-boot\"")
        assertContains(githubRequests[4], "Merge the companion PR #21 into this PR's branch first, then this PR.")
        assertEquals(1, git(repo, "worktree", "list").lines().size, "the base worktree is gone")
    }

    private fun command() =
        PrCommand(
            adapters = { listOf(MavenAdapter(it.mavenSettings)) },
            sources = ::sources,
            index = { RecipeIndex { _, _ -> RecipeSelection.NONE } },
            // The CLI only takes http(s) servers; the test's remote is a local directory.
            host = { event, apiUrl, _, token, botLogin -> GitHubHost(event, apiUrl, dir.resolve("server").toUri(), token, botLogin) },
        )

    /**
     * The origin with the base commit and Renovate's update on its branch. The Maven wrapper reports a compile error for
     * every `broken();` in `App.java`, which the update renamed to `fixed()`, and writes the effective POM when asked.
     */
    private fun renovatePr(): Pair<String, String> {
        val work = dir.resolve("origin-work")
        work.resolve("src/main/java").createDirectories()
        work.resolve("src/main/java/App.java").writeText("class App {\n    void run() {\n        broken();\n    }\n}\n")
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
        git(work, "-c", "user.name=Renovate Bot", "-c", "user.email=bot@renovateapp.com", "commit", "--quiet", "-am", "Update spring boot")
        val head = git(work, "rev-parse", "HEAD")
        git(dir, "init", "--quiet", "--bare", origin.toString())
        git(work, "push", "--quiet", origin.toString(), "main", "renovate/spring-boot")
        return base to head
    }

    private fun event(
        head: String,
        base: String,
        author: String = "renovate[bot]",
        labels: List<String> = emptyList(),
    ): Path {
        val body =
            "This PR contains the following updates:\n\n| Package | Change |\n|---|---|\n| $PARENT | `3.3.5` → `3.4.1` |\n\n" +
                "---\n\n### Release Notes\n\nRenamed broken() to fixed().\n\n---\n\n### Configuration\n\nNone."
        val pr =
            buildJsonObject {
                put("number", 7)
                put("state", "open")
                put("merged", false)
                put("labels", buildJsonArray { labels.forEach { add(buildJsonObject { put("name", it) }) } })
                put("body", body)
                put("user", buildJsonObject { put("login", author) })
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
}
