package gmbh.vanella.lector.llm

import gmbh.vanella.lector.core.FixRequest
import gmbh.vanella.lector.core.LlmClient
import gmbh.vanella.lector.core.Proposal
import gmbh.vanella.lector.core.ToolError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val logger = LoggerFactory.getLogger(OllamaClient::class.java)
private val CONNECT_TIMEOUT = 30.seconds

/**
 * Asks a local Ollama for edits through its native `/api/chat`, with the answer's JSON schema as `format`
 * (concept section 6.3). The context size is fixed, so memory use on the runner is predictable, and the answer's length
 * is capped. Ollama is the only outbound call of the fix loop (rule 7).
 */
class OllamaClient(
    private val url: URI,
    private val model: String,
    private val timeout: Duration = 15.minutes,
) : LlmClient {
    private val http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT.toJavaDuration()).build()
    private val endpoint = URI.create(url.toString().removeSuffix("/") + "/api/chat")

    override fun propose(request: FixRequest): Proposal {
        val prompt = Prompt.user(request)
        logger.debug("Asking {} at {}:\n{}", model, url, prompt)
        val body =
            buildJsonObject {
                put("model", model)
                put("stream", false)
                put("format", FixSchema.SCHEMA)
                putJsonObject("options") {
                    put("num_ctx", Prompt.CONTEXT_TOKENS)
                    put("num_predict", Prompt.ANSWER_TOKENS)
                    put("temperature", request.temperature)
                }
                putJsonArray("messages") {
                    add(message("system", Prompt.SYSTEM))
                    add(message("user", prompt))
                }
            }
        val response = send(body.toString())
        val status = response.statusCode()
        if (status != HTTP_OK) throw ToolError("Ollama at $url answered with status $status: ${response.body()}")
        val content = messageContent(response.body()) ?: return Proposal.Invalid("Ollama's reply has no message content")
        logger.debug("Ollama answered: {}", content)
        return FixSchema.parse(content)
    }

    private fun send(body: String): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(endpoint)
                .timeout(timeout.toJavaDuration())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        return try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: HttpTimeoutException) {
            throw ToolError("Ollama at $url did not answer within $timeout", e)
        } catch (e: IOException) {
            throw ToolError("Ollama is not reachable at $url: $e", e)
        }
    }

    private fun message(
        role: String,
        content: String,
    ) = JsonObject(mapOf("role" to JsonPrimitive(role), "content" to JsonPrimitive(content)))

    private fun messageContent(body: String): String? {
        val reply = FixSchema.json(body) as? JsonObject ?: return null
        val message = reply["message"] as? JsonObject ?: return null
        return (message["content"] as? JsonPrimitive)?.content
    }

    private companion object {
        const val HTTP_OK = 200
    }
}
