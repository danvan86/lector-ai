package gmbh.vanella.lector.git

import com.sun.net.httpserver.HttpServer
import gmbh.vanella.lector.core.PrState
import gmbh.vanella.lector.core.PullRequest
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.runProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val BOT = "lector-ai[bot]"

/** The GitHub adapter against a local fake of the REST API, and against local Git remotes. */
class GitHubHostTest {
    @TempDir
    lateinit var dir: Path

    private val token = "ghs_secret_token"
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val requests = mutableListOf<Request>()
    private val comments = mutableListOf<JsonObject>()
    private var nextCommentId = 100
    private val pulls = mutableListOf<JsonObject>()
    private var nextPull = 20
    private var refGone = false
    private var failWith: Int? = null

    private val pr = PullRequest(7, "renovate[bot]", "renovate/guava", "head", "acme/app", "acme/app", "base", "")

    private data class Request(
        val method: String,
        val path: String,
        val query: String?,
        val authorization: String?,
        val body: JsonObject?,
    )

    init {
        server.createContext("/") { exchange ->
            val text = exchange.requestBody.readAllBytes().decodeToString()
            val request =
                Request(
                    exchange.requestMethod,
                    exchange.requestURI.path,
                    exchange.requestURI.rawQuery,
                    exchange.requestHeaders.getFirst("Authorization"),
                    if (text.isEmpty()) null else Json.parseToJsonElement(text).jsonObject,
                )
            requests += request
            val (status, answer) = failWith?.let { it to """{"message":"Bad credentials"}""" } ?: answer(request)
            val bytes = answer.encodeToByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterTest
    fun `stop the server`() = server.stop(0)

    /** Comments of PR 7, 100 per page; creating and updating a comment answers with it. */
    private fun answer(request: Request): Pair<Int, String> =
        when {
            request.method == "GET" && request.path == "/repos/acme/app/issues/7/comments" -> {
                val page =
                    request.query
                        .orEmpty()
                        .substringAfter("page=")
                        .substringBefore('&')
                        .toInt()
                200 to buildJsonArray { comments.drop((page - 1) * 100).take(100).forEach { add(it) } }.toString()
            }

            request.method == "POST" && request.path == "/repos/acme/app/issues/7/comments" -> {
                val comment = comment(nextCommentId++, request.body!!["body"]!!.jsonPrimitive.content)
                comments += comment
                201 to comment.toString()
            }

            request.method == "PATCH" && request.path.startsWith("/repos/acme/app/issues/comments/") -> {
                200 to "{}"
            }

            request.method == "POST" && request.path.matches(Regex("/repos/acme/app/issues/\\d+/labels")) -> {
                200 to "[]"
            }

            request.path.startsWith("/repos/acme/app/pulls") || request.path.startsWith("/repos/acme/app/git/") -> {
                answerPulls(request)
            }

            else -> {
                404 to """{"message":"Not Found"}"""
            }
        }

    @Test
    fun `reads the PR from the pull_request event`() {
        val pr = host(event("renovate[bot]", "acme/app", body = "This PR contains the following updates:")).pullRequest()

        assertEquals(
            PullRequest(
                7,
                "renovate[bot]",
                "renovate/guava",
                "1111111",
                "acme/app",
                "acme/app",
                "2222222",
                "This PR contains the following updates:",
            ),
            pr,
        )
    }

    @Test
    fun `reads the PR's labels and whether it is open, closed, or merged`() {
        val labelled = host(event(labels = listOf("lector", "dependencies"))).pullRequest()
        val closed = host(event(state = "closed" to false)).pullRequest()
        val merged = host(event(state = "closed" to true)).pullRequest()

        assertEquals(setOf("lector", "dependencies"), labelled.labels)
        assertEquals(PrState.OPEN, labelled.state)
        assertEquals(PrState.CLOSED, closed.state)
        assertEquals(PrState.MERGED, merged.state)
    }

    @Test
    fun `a PR from a deleted fork has no head repository, and a PR without a body has an empty one`() {
        val pr = host(event("renovate[bot]", headRepository = null, body = null)).pullRequest()

        assertEquals("", pr.headRepository)
        assertEquals("", pr.body)
    }

    /** Open PRs, filtered by head and base; creating a PR answers with it; deleting a branch answers with nothing. */
    private fun answerPulls(request: Request): Pair<Int, String> =
        when {
            request.method == "GET" && request.path == "/repos/acme/app/pulls" -> {
                val query =
                    request.query
                        .orEmpty()
                        .split('&')
                        .associate { it.substringBefore('=') to it.substringAfter('=') }
                val head = java.net.URLDecoder.decode(query.getValue("head"), Charsets.UTF_8)
                val base = java.net.URLDecoder.decode(query.getValue("base"), Charsets.UTF_8)
                val open =
                    pulls.filter {
                        "acme:" + it["head"]!!.jsonObject["ref"]!!.jsonPrimitive.content == head &&
                            it["base"]!!.jsonObject["ref"]!!.jsonPrimitive.content == base
                    }
                200 to buildJsonArray { open.forEach { add(it) } }.toString()
            }

            request.method == "POST" && request.path == "/repos/acme/app/pulls" -> {
                val body = request.body!!
                val pull = pull(nextPull++, body["head"]!!.jsonPrimitive.content, body["base"]!!.jsonPrimitive.content, BOT)
                pulls += pull
                201 to pull.toString()
            }

            request.method == "PATCH" && request.path.startsWith("/repos/acme/app/pulls/") -> {
                200 to "{}"
            }

            request.method == "DELETE" && request.path.startsWith("/repos/acme/app/git/refs/heads/") -> {
                if (refGone) 422 to """{"message":"Reference does not exist"}""" else 204 to ""
            }

            else -> {
                404 to """{"message":"Not Found"}"""
            }
        }

    @Test
    fun `an event that isn't about a pull request is a tool error`() {
        val event = dir.resolve("push.json")
        event.writeText("""{"ref": "refs/heads/main"}""")

        val error = assertFailsWith<ToolError> { host(event).pullRequest() }

        assertEquals("The event in $event is not a pull_request event", error.message)
    }

    @Test
    fun `a pull request without its fields is a tool error`() {
        val event = dir.resolve("event.json")
        event.writeText("""{"pull_request": {"number": 7}}""")
        val wrongType = dir.resolve("wrong.json")
        wrongType.writeText("""{"pull_request": {"head": "main", "base": {}}}""")

        val missing = assertFailsWith<ToolError> { host(event).pullRequest() }
        val wrong = assertFailsWith<ToolError> { host(wrongType).pullRequest() }

        assertContains(missing.message, "The pull request in the event in $event is incomplete:")
        assertContains(wrong.message, "The pull request in the event in $wrongType is incomplete:")
    }

    @Test
    fun `an event that isn't JSON is a tool error`() {
        val event = dir.resolve("event.json")
        event.writeText("not json")

        val error = assertFailsWith<ToolError> { host(event).pullRequest() }

        assertContains(error.message, "The event in $event is not valid JSON")
    }

    @Test
    fun `the first comment is created with the marker, and every later one updates it`() {
        val host = host()

        host.comment(pr, "first report", create = true)
        host.comment(pr, "second report", create = true)

        val writes = requests.filter { it.method != "GET" }
        assertEquals(
            listOf("POST /repos/acme/app/issues/7/comments", "PATCH /repos/acme/app/issues/comments/100"),
            writes.map {
                "${it.method} ${it.path}"
            },
        )
        assertEquals("<!-- lector-ai -->\nfirst report", writes[0].body!!["body"]!!.jsonPrimitive.content)
        assertEquals("<!-- lector-ai -->\nsecond report", writes[1].body!!["body"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the marked comment is found on a later page, among other comments`() {
        repeat(100) { comments += comment(it, "LGTM $it") }
        comments += comment(555, "<!-- lector-ai -->\nold report")

        host().comment(pr, "new report", create = true)

        assertEquals(listOf("page=1&per_page=100", "page=2&per_page=100"), requests.filter { it.method == "GET" }.map { it.query })
        assertEquals("PATCH /repos/acme/app/issues/comments/555", requests.last().let { "${it.method} ${it.path}" })
    }

    @Test
    fun `a marked comment by anyone but the bot is not the report`() {
        comments += comment(1, "<!-- lector-ai -->\nlooks like a report", author = "mallory")

        host().comment(pr, "report", create = true)

        assertEquals(
            listOf("GET /repos/acme/app/issues/7/comments", "POST /repos/acme/app/issues/7/comments"),
            requests.map { "${it.method} ${it.path}" },
        )
    }

    @Test
    fun `without a marked comment and without create, nothing is written`() {
        comments += comment(1, "LGTM")

        host().comment(pr, "green", create = false)

        assertEquals(listOf("GET"), requests.map { it.method })
    }

    @Test
    fun `adds a label to a PR by its number, in the repository of the event's PR`() {
        host().label(21, "ai-assisted")

        val request = requests.single()
        assertEquals("POST /repos/acme/app/issues/21/labels", "${request.method} ${request.path}")
        assertEquals(listOf("ai-assisted"), request.body!!["labels"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `every request carries the token, and an error status is a tool error with GitHub's message`() {
        failWith = 401

        val error = assertFailsWith<ToolError> { host().label(7, "ai-assisted") }

        assertEquals("Bearer $token", requests.single().authorization)
        assertEquals(
            "GitHub answered POST /repos/acme/app/issues/7/labels with status 401: {\"message\":\"Bad credentials\"}",
            error.message,
        )
    }

    @Test
    fun `an unreachable API is a tool error`() {
        val unreachable = GitHubHost(event(), URI("http://127.0.0.1:1"), URI("https://github.com"), token, BOT)

        val error = assertFailsWith<ToolError> { unreachable.label(7, "ai-assisted") }

        assertContains(error.message, "Cannot reach GitHub at http://127.0.0.1:1")
    }

    @Test
    fun `finds the open PR from a head into a base that the bot opened, and no other`() {
        pulls += pull(11, "heal/dependabot/maven/guava", "dependabot/maven/guava", "mallory")
        pulls += pull(12, "heal/dependabot/maven/guava", "dependabot/maven/guava", BOT)

        val found = host().findPullRequest("heal/dependabot/maven/guava", "dependabot/maven/guava")
        val none = host().findPullRequest("heal/renovate/guava", "renovate/guava")

        assertEquals(12, found)
        assertEquals(null, none)
        assertEquals(
            "state=open&head=acme%3Aheal%2Fdependabot%2Fmaven%2Fguava&base=dependabot%2Fmaven%2Fguava&per_page=100",
            requests.first().query,
        )
    }

    @Test
    fun `creates a PR and returns its number`() {
        val number = host().createPullRequest("heal/dependabot/maven/guava", "dependabot/maven/guava", "Fixes", "The summary")

        assertEquals(20, number)
        val request = requests.single()
        assertEquals("POST /repos/acme/app/pulls", "${request.method} ${request.path}")
        assertEquals(
            mapOf(
                "title" to "Fixes",
                "head" to "heal/dependabot/maven/guava",
                "base" to "dependabot/maven/guava",
                "body" to "The summary",
            ),
            request.body!!.mapValues { it.value.jsonPrimitive.content },
        )
    }

    @Test
    fun `updates a PR's description and closes a PR`() {
        val host = host()

        host.updatePullRequest(20, "New summary")
        host.closePullRequest(20)

        assertEquals(listOf("PATCH /repos/acme/app/pulls/20", "PATCH /repos/acme/app/pulls/20"), requests.map { "${it.method} ${it.path}" })
        assertEquals(
            listOf(mapOf("body" to "New summary"), mapOf("state" to "closed")),
            requests.map { r ->
                r.body!!.mapValues { it.value.jsonPrimitive.content }
            },
        )
    }

    @Test
    fun `deletes a branch, and one that is already gone is no error`() {
        host().deleteBranch("heal/dependabot/maven/guava")
        refGone = true
        host().deleteBranch("heal/dependabot/maven/guava")

        assertEquals(
            listOf(
                "DELETE /repos/acme/app/git/refs/heads/heal/dependabot/maven/guava",
                "DELETE /repos/acme/app/git/refs/heads/heal/dependabot/maven/guava",
            ),
            requests.map { "${it.method} ${it.path}" },
        )
    }

    @Test
    fun `a branch that can't be deleted for another reason is a tool error`() {
        failWith = 403

        val error = assertFailsWith<ToolError> { host().deleteBranch("heal/renovate/guava") }

        assertEquals(
            "GitHub answered DELETE /repos/acme/app/git/refs/heads/heal/renovate/guava with status 403: {\"message\":\"Bad credentials\"}",
            error.message,
        )
    }

    @Test
    fun `a force-push goes only to a companion branch, never to any other`() {
        val runs = mutableListOf<List<String>>()
        val host =
            GitHubHost(event(), apiUrl(), URI("https://github.example.com/"), token, BOT) { command, _, _ ->
                runs += command
                gmbh.vanella.lector.core
                    .ProcessResult(0, "")
            }

        host.forcePush(dir, "heal/renovate/guava")
        val error = assertFailsWith<ToolError> { host.forcePush(dir, "renovate/guava") }

        assertEquals(
            listOf(
                listOf(
                    "git",
                    "push",
                    "--quiet",
                    "--force",
                    "https://github.example.com/acme/app.git",
                    "HEAD:refs/heads/heal/renovate/guava",
                ),
            ),
            runs,
        )
        assertEquals("Only a companion branch (heal/…) is pushed by force, not renovate/guava", error.message)
    }

    @Test
    fun `a force-push replaces a companion branch that moved on, and one that fails is a tool error`() {
        val remote = dir.resolve("server/acme/app.git")
        git(dir, "init", "--quiet", "--bare", remote.toString())
        val first = dir.resolve("first")
        git(dir, "init", "--quiet", first.toString())
        commit(first, "old fixes")
        git(first, "push", "--quiet", remote.toString(), "HEAD:refs/heads/heal/renovate/guava")
        val rebuilt = dir.resolve("rebuilt")
        git(dir, "init", "--quiet", rebuilt.toString())
        commit(rebuilt, "new fixes")

        GitHubHost(event(), apiUrl(), dir.resolve("server").toUri(), token, BOT).forcePush(rebuilt, "heal/renovate/guava")

        assertEquals(git(rebuilt, "rev-parse", "HEAD"), git(remote, "rev-parse", "heal/renovate/guava"))
        val nowhere = GitHubHost(event(), apiUrl(), dir.resolve("nowhere").toUri(), token, BOT)
        assertContains(assertFailsWith<ToolError> { nowhere.forcePush(rebuilt, "heal/renovate/guava") }.message, "git push failed:")
    }

    @Test
    fun `fetch and push authenticate with the token in the environment, never on the command line, and push never forces`() {
        val runs = mutableListOf<Pair<List<String>, Map<String, String>>>()
        val host =
            GitHubHost(event(), apiUrl(), URI("https://github.example.com/"), token, BOT) { command, _, environment ->
                runs += command to environment
                gmbh.vanella.lector.core
                    .ProcessResult(0, "")
            }

        host.fetch(dir, pr, "2222222")
        assertTrue(host.push(dir, pr))

        assertEquals(
            listOf(
                listOf("git", "fetch", "--quiet", "--no-tags", "--depth=1", "https://github.example.com/acme/app.git", "2222222"),
                listOf("git", "push", "--quiet", "https://github.example.com/acme/app.git", "HEAD:refs/heads/renovate/guava"),
            ),
            runs.map { it.first },
        )
        runs.forEach { (command, _) ->
            assertFalse(command.any { token in it }, "no token in $command")
            assertFalse(command.any { it.startsWith("--force") || it.startsWith("+") || it == "-f" }, "no force in $command")
        }
        val header =
            "AUTHORIZATION: basic " +
                java.util.Base64
                    .getEncoder()
                    .encodeToString("x-access-token:$token".toByteArray())
        assertEquals(
            mapOf(
                "GIT_TERMINAL_PROMPT" to "0",
                "GIT_CONFIG_COUNT" to "2",
                "GIT_CONFIG_KEY_0" to "http.https://github.example.com/.extraheader",
                "GIT_CONFIG_VALUE_0" to "",
                "GIT_CONFIG_KEY_1" to "http.https://github.example.com/.extraheader",
                "GIT_CONFIG_VALUE_1" to header,
            ),
            runs.first().second,
        )
    }

    @Test
    fun `push to a branch that didn't move goes through, and to one that moved is rejected`() {
        val remote = dir.resolve("server/acme/app.git")
        git(dir, "init", "--quiet", "--bare", remote.toString())
        val clone = dir.resolve("clone")
        git(dir, "init", "--quiet", clone.toString())
        commit(clone, "update")
        git(clone, "push", "--quiet", remote.toString(), "HEAD:refs/heads/renovate/guava")
        val host = GitHubHost(event(), apiUrl(), dir.resolve("server").toUri(), token, BOT)

        commit(clone, "fix")
        assertTrue(host.push(clone, pr))
        assertEquals(git(clone, "rev-parse", "HEAD"), git(remote, "rev-parse", "renovate/guava"))

        val other = dir.resolve("other")
        git(dir, "clone", "--quiet", "--branch", "renovate/guava", remote.toString(), other.toString())
        commit(other, "rebase by Renovate")
        git(other, "push", "--quiet", "origin", "renovate/guava")
        commit(clone, "another fix")
        assertFalse(host.push(clone, pr))
        assertEquals(git(other, "rev-parse", "HEAD"), git(remote, "rev-parse", "renovate/guava"))
    }

    @Test
    fun `a push or fetch that fails for another reason is a tool error`() {
        val repo = dir.resolve("repo")
        git(dir, "init", "--quiet", repo.toString())
        commit(repo, "update")
        val host = GitHubHost(event(), apiUrl(), dir.resolve("nowhere").toUri(), token, BOT)

        val push = assertFailsWith<ToolError> { host.push(repo, pr) }
        val fetch = assertFailsWith<ToolError> { host.fetch(repo, pr, "2222222") }

        assertContains(push.message, "git push failed:")
        assertContains(fetch.message, "git fetch failed:")
    }

    private fun host(event: Path = event()) = GitHubHost(event, apiUrl(), URI("https://github.com"), token, BOT)

    private fun apiUrl() = URI("http://127.0.0.1:${server.address.port}/")

    private fun event(
        author: String = "renovate[bot]",
        headRepository: String? = "acme/app",
        body: String? = "",
        state: Pair<String, Boolean> = "open" to false,
        labels: List<String> = emptyList(),
    ): Path {
        val event =
            buildJsonObject {
                put("action", "synchronize")
                put(
                    "pull_request",
                    buildJsonObject {
                        put("number", 7)
                        put("state", state.first)
                        put("merged", state.second)
                        put("labels", buildJsonArray { labels.forEach { add(buildJsonObject { put("name", it) }) } })
                        put("body", body)
                        put("user", buildJsonObject { put("login", author) })
                        put(
                            "head",
                            buildJsonObject {
                                put("ref", "renovate/guava")
                                put("sha", "1111111")
                                put("repo", if (headRepository == null) null else buildJsonObject { put("full_name", headRepository) })
                            },
                        )
                        put(
                            "base",
                            buildJsonObject {
                                put("ref", "main")
                                put("sha", "2222222")
                                put("repo", buildJsonObject { put("full_name", "acme/app") })
                            },
                        )
                    },
                )
            }
        val file = dir.resolve("event-${event.hashCode()}.json")
        file.writeText(event.toString())
        return file
    }

    private fun pull(
        number: Int,
        head: String,
        base: String,
        author: String,
    ) = buildJsonObject {
        put("number", number)
        put("user", buildJsonObject { put("login", author) })
        put("head", buildJsonObject { put("ref", head) })
        put("base", buildJsonObject { put("ref", base) })
    }

    private fun comment(
        id: Int,
        body: String,
        author: String = BOT,
    ) = buildJsonObject {
        put("id", id)
        put("body", body)
        put("user", buildJsonObject { put("login", author) })
    }

    private fun commit(
        repo: Path,
        message: String,
    ) = git(repo, "-c", "user.name=Dev", "-c", "user.email=dev@example.com", "commit", "--quiet", "--allow-empty", "-m", message)

    private fun git(
        where: Path,
        vararg args: String,
    ): String {
        val result = runProcess(listOf("git", "-c", "commit.gpgsign=false") + args, where, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output.trim()
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.put(
        key: String,
        value: JsonObject?,
    ) = put(key, value ?: kotlinx.serialization.json.JsonNull)
}
