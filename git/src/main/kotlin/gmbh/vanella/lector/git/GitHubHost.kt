package gmbh.vanella.lector.git

import gmbh.vanella.lector.core.COMPANION_PREFIX
import gmbh.vanella.lector.core.GitHost
import gmbh.vanella.lector.core.PrState
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.PullRequest
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.runProcess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val logger = LoggerFactory.getLogger(GitHubHost::class.java)

private val GIT_TIMEOUT = 10.minutes

// The hidden line that marks the tool's one comment on a PR.
private const val MARKER = "<!-- lector-ai -->"
private const val PAGE_SIZE = 100

// What GitHub answers when a branch to delete is already gone, e.g. deleted on merge.
private const val HTTP_UNPROCESSABLE = 422

/**
 * GitHub as the Git host: the PR from the `pull_request` event the workflow received, the report comment and the
 * label, and the companion PR through the REST API, and fetch and push with the `git` CLI. [token] is the GitHub App's installation token:
 * pushes with it start the PR's checks again, unlike pushes with `GITHUB_TOKEN`. It only reaches `git` through its
 * environment, never the command line, and it never reaches a build. [botLogin] is the App's bot, `<app-slug>[bot]`:
 * only its comment is the report and only its PR is a companion PR. Never merges, approves, or requests reviews.
 */
class GitHubHost(
    private val event: Path,
    apiUrl: URI,
    serverUrl: URI,
    private val token: String,
    private val botLogin: String,
    private val git: (
        command: List<String>,
        workDir: Path,
        environment: Map<String, String>,
    ) -> ProcessResult = { command, workDir, environment ->
        runProcess(command, workDir, GIT_TIMEOUT, environment)
    },
) : GitHost {
    private val api = GitHubApi(apiUrl.toString().removeSuffix("/"), token)
    private val server = serverUrl.toString().removeSuffix("/")

    // The repository of the run's PR: every PR and branch the host touches is in it.
    private val repository by lazy { pullRequest().baseRepository }

    override fun pullRequest(): PullRequest {
        val pr = eventJson()["pull_request"] as? JsonObject ?: throw ToolError("The event in $event is not a pull_request event")
        return try {
            pullRequest(pr)
        } catch (e: IllegalArgumentException) {
            throw ToolError("The pull request in the event in $event is incomplete: ${e.message}", e)
        }
    }

    private fun eventJson(): JsonObject =
        try {
            Json.parseToJsonElement(event.readText()).jsonObject
        } catch (e: SerializationException) {
            throw ToolError("The event in $event is not valid JSON: ${e.message}", e)
        }

    private fun pullRequest(pr: JsonObject): PullRequest {
        val head = pr.field("head").jsonObject
        val base = pr.field("base").jsonObject
        val headRepository = head.field("repo")
        val body = pr.field("body")
        return PullRequest(
            number = pr.field("number").jsonPrimitive.int,
            author = pr.field("user").jsonObject.string("login"),
            headBranch = head.string("ref"),
            headSha = head.string("sha"),
            // A fork that was deleted has no repository: certainly not the base's.
            headRepository = if (headRepository is JsonNull) "" else headRepository.jsonObject.string("full_name"),
            baseRepository = base.field("repo").jsonObject.string("full_name"),
            baseSha = base.string("sha"),
            body = if (body is JsonNull) "" else body.jsonPrimitive.content,
            labels =
                pr
                    .field("labels")
                    .jsonArray
                    .map { it.jsonObject.string("name") }
                    .toSet(),
            state = state(pr),
        )
    }

    private fun state(pr: JsonObject) =
        when {
            pr.string("state") == "open" -> PrState.OPEN
            pr.field("merged").jsonPrimitive.boolean -> PrState.MERGED
            else -> PrState.CLOSED
        }

    override fun fetch(
        repo: Path,
        pr: PullRequest,
        commit: String,
    ) {
        val result =
            git(listOf("git", "fetch", "--quiet", "--no-tags", "--depth=1", remote(pr.baseRepository), commit), repo, authentication())
        if (result.exitCode != 0) throw ToolError("git fetch failed: ${result.output.trim()}")
    }

    override fun push(
        repo: Path,
        pr: PullRequest,
    ): Boolean {
        val command = listOf("git", "push", "--quiet", remote(pr.baseRepository), "HEAD:refs/heads/${pr.headBranch}")
        val result = git(command, repo, authentication())
        return when {
            result.exitCode == 0 -> true

            // The branch moved on, e.g. Renovate rebased it: not a failure of the tool.
            "[rejected]" in result.output -> false.also { logger.warn("GitHub rejected the push: {}", result.output.trim()) }

            else -> throw ToolError("git push failed: ${result.output.trim()}")
        }
    }

    override fun comment(
        pr: PullRequest,
        body: String,
        create: Boolean,
    ) {
        val marked = JsonObject(mapOf("body" to JsonPrimitive("$MARKER\n$body")))
        val existing = markedComment(pr)
        when {
            existing != null -> api.send("PATCH", "/repos/${pr.baseRepository}/issues/comments/$existing", marked)
            create -> api.send("POST", "/repos/${pr.baseRepository}/issues/${pr.number}/comments", marked)
            else -> logger.info("No report comment on the PR yet, and none is created")
        }
    }

    override fun label(
        number: Int,
        label: String,
    ) {
        api.send("POST", "/repos/$repository/issues/$number/labels", JsonObject(mapOf("labels" to JsonArray(listOf(JsonPrimitive(label))))))
    }

    override fun forcePush(
        repo: Path,
        branch: String,
    ) {
        requireCompanion(branch, "pushed by force")
        val result = git(listOf("git", "push", "--quiet", "--force", remote(repository), "HEAD:refs/heads/$branch"), repo, authentication())
        if (result.exitCode != 0) throw ToolError("git push failed: ${result.output.trim()}")
    }

    override fun findPullRequest(
        head: String,
        base: String,
    ): Int? {
        val owner = repository.substringBefore('/')
        val query = "state=open&head=${encode("$owner:$head")}&base=${encode(base)}&per_page=$PAGE_SIZE"
        val pull =
            api
                .send("GET", "/repos/$repository/pulls?$query", null)
                .jsonArray
                .map { it.jsonObject }
                .firstOrNull { it.field("user").jsonObject.string("login") == botLogin }
        return if (pull == null) null else pull.field("number").jsonPrimitive.int
    }

    override fun createPullRequest(
        head: String,
        base: String,
        title: String,
        body: String,
    ): Int {
        val fields = mapOf("title" to title, "head" to head, "base" to base, "body" to body).mapValues { JsonPrimitive(it.value) }
        return api
            .send("POST", "/repos/$repository/pulls", JsonObject(fields))
            .jsonObject
            .field("number")
            .jsonPrimitive.int
    }

    override fun updatePullRequest(
        number: Int,
        body: String,
    ) {
        api.send("PATCH", "/repos/$repository/pulls/$number", JsonObject(mapOf("body" to JsonPrimitive(body))))
    }

    override fun closePullRequest(number: Int) {
        api.send("PATCH", "/repos/$repository/pulls/$number", JsonObject(mapOf("state" to JsonPrimitive("closed"))))
    }

    override fun deleteBranch(branch: String) {
        requireCompanion(branch, "deleted")
        val path = "/repos/$repository/git/refs/heads/$branch"
        val response = api.request("DELETE", path, null)
        if (response.statusCode() == HTTP_UNPROCESSABLE) {
            logger.info("The branch {} is already gone: {}", branch, response.body())
        } else {
            api.check("DELETE", path, response)
        }
    }

    /**
     * The id of the bot's comment with the marker, or null. Anyone can write the marker, but only the bot's comment is
     * the report. Reads the PR's comments page by page until it finds it.
     */
    private fun markedComment(pr: PullRequest): Long? {
        var page = 1
        while (true) {
            val comments =
                api
                    .send(
                        "GET",
                        "/repos/${pr.baseRepository}/issues/${pr.number}/comments?page=$page&per_page=$PAGE_SIZE",
                        null,
                    ).jsonArray
            val marked =
                comments.map { it.jsonObject }.firstOrNull {
                    it.field("user").jsonObject.string("login") == botLogin && it.string("body").startsWith(MARKER)
                }
            if (marked != null) {
                return marked.string("id").toLong()
            }
            if (comments.size < PAGE_SIZE) return null
            page++
        }
    }

    /** Fails unless [branch] is a companion branch: the only branches the tool overwrites or deletes. */
    private fun requireCompanion(
        branch: String,
        what: String,
    ) {
        if (!branch.startsWith(COMPANION_PREFIX)) throw ToolError("Only a companion branch ($COMPANION_PREFIX…) is $what, not $branch")
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    private fun remote(repository: String) = "$server/$repository.git"

    /**
     * Git's configuration through the environment: the token as the HTTP header for the server, after an empty value that
     * drops any header `actions/checkout` configured with `GITHUB_TOKEN`. No prompt: a CI run can't answer one.
     */
    private fun authentication(): Map<String, String> {
        val key = "http.$server/.extraheader"
        val credentials = Base64.getEncoder().encodeToString("x-access-token:$token".toByteArray())
        return mapOf(
            "GIT_TERMINAL_PROMPT" to "0",
            "GIT_CONFIG_COUNT" to "2",
            "GIT_CONFIG_KEY_0" to key,
            "GIT_CONFIG_VALUE_0" to "",
            "GIT_CONFIG_KEY_1" to key,
            "GIT_CONFIG_VALUE_1" to "AUTHORIZATION: basic $credentials",
        )
    }

    private fun JsonObject.field(name: String): JsonElement = requireNotNull(get(name)) { "it has no $name" }

    private fun JsonObject.string(name: String) = field(name).jsonPrimitive.content
}
