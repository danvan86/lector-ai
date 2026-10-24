package gmbh.vanella.lector.llm

import com.sun.net.httpserver.HttpServer
import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Edit
import gmbh.vanella.lector.core.FixRequest
import gmbh.vanella.lector.core.Proposal
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.Snippet
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.UpdateType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Runs the client against a local fake of Ollama's `/api/chat`. */
class OllamaClientTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val requests = mutableListOf<Pair<String, JsonObject>>()
    private var status = 200
    private var content =
        """{"analysis":"moved","rationale":"Use MoreObjects.firstNonNull","edits":[{"file":"App.java","search":"Objects.","replace":"MoreObjects."}]}"""
    private var body: String? = null
    private var delayMillis = 0L

    private val url: URI

    init {
        server.createContext("/") { exchange ->
            requests += exchange.requestURI.path to Json.parseToJsonElement(exchange.requestBody.readAllBytes().decodeToString()).jsonObject
            Thread.sleep(delayMillis)
            val answer = (body ?: chatResponse(content)).encodeToByteArray()
            exchange.sendResponseHeaders(status, answer.size.toLong())
            exchange.responseBody.use { it.write(answer) }
        }
        server.start()
        url = URI("http://127.0.0.1:${server.address.port}/")
    }

    private val request =
        FixRequest(
            changes = listOf(DependencyChange("maven", "com.google.guava:guava", "18.0", "21.0", UpdateType.MAJOR)),
            diagnostics = listOf(Diagnostic("App.java", 3, 9, null, "cannot find symbol", Severity.ERROR, "javac")),
            snippets = emptyList(),
            grounding = "",
            releaseNotes = null,
            recipeDiff = null,
            previousAttempts = emptyList(),
            temperature = 0.0,
        )

    @AfterTest
    fun `stop the server`() = server.stop(0)

    @Test
    fun `posts to api_chat with the model, no streaming, the schema as format, and a fixed context size`() {
        OllamaClient(url, "qwen2.5-coder:7b").propose(request)

        val (path, json) = requests.single()
        assertEquals("/api/chat", path)
        assertEquals("qwen2.5-coder:7b", json.getValue("model").jsonPrimitive.content)
        assertEquals(
            false,
            json
                .getValue("stream")
                .jsonPrimitive.content
                .toBooleanStrict(),
        )
        assertEquals(FixSchema.SCHEMA, json.getValue("format"))
        val options = json.getValue("options").jsonObject
        assertEquals(16384, options.getValue("num_ctx").jsonPrimitive.int)
        assertEquals(0.0, options.getValue("temperature").jsonPrimitive.double)
        val messages = json.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user"), messages.map { it.getValue("role").jsonPrimitive.content })
        assertEquals(Prompt.SYSTEM, messages[0].getValue("content").jsonPrimitive.content)
        assertEquals(Prompt.user(request), messages[1].getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `a request that doesn't fit into the context is still sent in full`() {
        val huge = request.copy(snippets = List(10) { Snippet("App.java", it * 100 + 1, it * 100 + 1, "x".repeat(5_000) + "\n") })

        OllamaClient(url, "qwen2.5-coder:7b").propose(huge)

        val messages =
            requests
                .single()
                .second
                .getValue("messages")
                .jsonArray
        assertEquals(
            Prompt.user(huge),
            messages[1]
                .jsonObject
                .getValue("content")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `a request fits if its errors, code, and earlier attempts fit, whatever the size of the optional parts`() {
        val client = OllamaClient(url, "qwen2.5-coder:7b")
        val huge = request.copy(snippets = List(10) { Snippet("App.java", it * 100 + 1, it * 100 + 1, "x".repeat(5_000) + "\n") })

        assertTrue(client.fits(request.copy(grounding = "g".repeat(100_000))))
        assertFalse(client.fits(huge))
        assertEquals(emptyList(), requests, "checking sends nothing")
    }

    @Test
    fun `the schema requires an analysis first, a rationale, and a list of edits with file, search, and replace`() {
        val schema = FixSchema.SCHEMA
        assertEquals("[\"analysis\",\"rationale\",\"edits\"]", schema.getValue("required").toString())
        val edit =
            schema
                .getValue("properties")
                .jsonObject
                .getValue("edits")
                .jsonObject
                .getValue("items")
                .jsonObject
        assertEquals("[\"file\",\"search\",\"replace\"]", edit.getValue("required").toString())
    }

    @Test
    fun `sends the request's temperature`() {
        OllamaClient(url, "m").propose(request.copy(temperature = 0.3))

        assertEquals(
            0.3,
            requests
                .single()
                .second
                .getValue("options")
                .jsonObject
                .getValue("temperature")
                .jsonPrimitive.double,
        )
    }

    @Test
    fun `a matching answer becomes edits with a rationale`() {
        val proposal = OllamaClient(url, "m").propose(request)

        assertEquals(Proposal.Edits(listOf(Edit("App.java", "Objects.", "MoreObjects.")), "Use MoreObjects.firstNonNull"), proposal)
    }

    @Test
    fun `code fences around the JSON are stripped`() {
        content = "```json\n{\"analysis\":\"a\",\"rationale\":\"r\",\"edits\":[]}\n```\n"

        assertEquals(Proposal.Edits(emptyList(), "r"), OllamaClient(url, "m").propose(request))
    }

    @Test
    fun `an answer that doesn't match the schema is invalid, not an error`() {
        val answers =
            mapOf(
                "Sure! Here is the fix." to "the answer is not JSON",
                "[]" to "the answer is not a JSON object",
                """{"rationale":"r","edits":[]}""" to "analysis is missing or not a string",
                """{"analysis":"a","edits":[]}""" to "rationale is missing or not a string",
                """{"analysis":"a","rationale":"r"}""" to "edits is missing or not a list",
                """{"analysis":"a","rationale":"r","edits":[{"file":"A.java","search":"a"}]}""" to
                    "edit 1: replace is missing or not a string",
                """{"analysis":"a","rationale":"r","edits":["a"]}""" to "edit 1 is not an object",
                """{"analysis":"a","rationale":["r"],"edits":[]}""" to "rationale is missing or not a string",
                """{"analysis":"a","rationale":1,"edits":[]}""" to "rationale is missing or not a string",
                """{"analysis":"a","rationale":"r","edits":[{"search":"a","replace":"b"}]}""" to "edit 1: file is missing or not a string",
                """{"analysis":"a","rationale":"r","edits":[{"file":"A.java","search":null,"replace":"b"}]}""" to
                    "edit 1: search is missing or not a string",
            )
        answers.forEach { (answer, reason) ->
            content = answer

            assertEquals(Proposal.Invalid(reason), OllamaClient(url, "m").propose(request), answer)
        }
    }

    @Test
    fun `a reply without a message is invalid`() {
        listOf("""{"done":true}""", "[]", """{"message":{"role":"assistant"}}""", "not JSON").forEach { reply ->
            body = reply

            assertEquals(Proposal.Invalid("Ollama's reply has no message content"), OllamaClient(url, "m").propose(request), reply)
        }
    }

    @Test
    fun `an error status is a tool error with Ollama's message`() {
        status = 404
        body = """{"error":"model 'nope' not found"}"""

        val error = assertFailsWith<ToolError> { OllamaClient(url, "nope").propose(request) }

        assertEquals("Ollama at $url answered with status 404: {\"error\":\"model 'nope' not found\"}", error.message)
    }

    @Test
    fun `an unreachable Ollama is a tool error`() {
        val port = ServerSocket(0).use { it.localPort }

        val error = assertFailsWith<ToolError> { OllamaClient(URI("http://127.0.0.1:$port"), "m").propose(request) }

        assertContains(error.message, "Ollama is not reachable at http://127.0.0.1:$port")
    }

    @Test
    fun `an Ollama that doesn't answer in time is no answer, not a tool error`() {
        delayMillis = 2_000

        val proposal = OllamaClient(url, "m", timeout = 1.seconds).propose(request)

        assertEquals(Proposal.Unanswered("Ollama at $url did not answer within 1s"), proposal)
    }

    private fun chatResponse(content: String) =
        buildJsonObject {
            put("model", "m")
            put(
                "message",
                buildJsonObject {
                    put("role", "assistant")
                    put("content", JsonPrimitive(content))
                },
            )
            put("done", true)
        }.toString()
}
