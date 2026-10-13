package gmbh.vanella.lector.git

import gmbh.vanella.lector.core.ToolError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val CONNECT_TIMEOUT = 30.seconds
private val REQUEST_TIMEOUT = 1.minutes
private const val HTTP_STATUS_CLASS = 100
private const val HTTP_SUCCESS = 2

/** GitHub's REST API at [url], every request with [token]. */
internal class GitHubApi(
    private val url: String,
    private val token: String,
) {
    private val http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT.toJavaDuration()).build()

    /** Sends a request and returns the JSON GitHub answered. Fails with a [ToolError] on an error status. */
    fun send(
        method: String,
        path: String,
        body: JsonObject?,
    ): JsonElement = Json.parseToJsonElement(check(method, path, request(method, path, body)).body())

    /** [response], if its status is a success. Fails with a [ToolError] that has GitHub's message otherwise. */
    fun check(
        method: String,
        path: String,
        response: HttpResponse<String>,
    ): HttpResponse<String> {
        val status = response.statusCode()
        if (status / HTTP_STATUS_CLASS != HTTP_SUCCESS) {
            throw ToolError("GitHub answered $method ${path.substringBefore('?')} with status $status: ${response.body()}")
        }
        return response
    }

    /** Sends a request and returns GitHub's answer, whatever its status. Fails with a [ToolError] if GitHub can't be reached. */
    fun request(
        method: String,
        path: String,
        body: JsonObject?,
    ): HttpResponse<String> {
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body.toString())
        val request =
            HttpRequest
                .newBuilder(URI.create(url + path))
                .timeout(REQUEST_TIMEOUT.toJavaDuration())
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build()
        return try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw ToolError("Cannot reach GitHub at $url: $e", e)
        }
    }
}
