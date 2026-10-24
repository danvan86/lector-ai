package gmbh.vanella.lector.git

import com.sun.net.httpserver.HttpServer
import gmbh.vanella.lector.core.CommitIdentity
import gmbh.vanella.lector.core.ToolError
import java.net.InetSocketAddress
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GitHubAppIdentityTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val requests = mutableListOf<String>()
    private var answer = 200 to """{"login": "my-lector[bot]", "id": 123, "type": "Bot"}"""

    init {
        server.createContext("/") { exchange ->
            requests += "${exchange.requestMethod} ${exchange.requestURI.rawPath} ${exchange.requestHeaders.getFirst("Authorization")}"
            val bytes = answer.second.encodeToByteArray()
            exchange.sendResponseHeaders(answer.first, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterTest
    fun `stop the server`() = server.stop(0)

    @Test
    fun `the App's bot is named slug-bot and gets the noreply email with its user id`() {
        val identity = gitHubAppIdentity(apiUrl(), "ghs_token", "my-lector")

        assertEquals(CommitIdentity("my-lector[bot]", "123+my-lector[bot]@users.noreply.github.com"), identity)
        assertEquals(listOf("GET /users/my-lector%5Bbot%5D Bearer ghs_token"), requests)
    }

    @Test
    fun `an API URL with a trailing slash asks the same path`() {
        gitHubAppIdentity(URI("${apiUrl()}/"), "ghs_token", "my-lector")

        assertEquals(listOf("GET /users/my-lector%5Bbot%5D Bearer ghs_token"), requests)
    }

    @Test
    fun `an answer that isn't the App's bot is a tool error that names the slug and the answer`() {
        listOf(
            """{"login":"my-lector[bot]","id":123,"type":"User"}""",
            """{"login":"other[bot]","id":123,"type":"Bot"}""",
            """{"login":"my-lector[bot]","type":"Bot"}""",
            """{"id":123,"type":"Bot"}""",
            """{"login":"my-lector[bot]","id":123}""",
            """{"login":"my-lector[bot]","id":0,"type":"Bot"}""",
            """{"login":"my-lector[bot]","id":-5,"type":"Bot"}""",
            """{"login":"my-lector[bot]","id":"123","type":"Bot"}""",
            """{"login":"my-lector[bot]","id":"abc","type":"Bot"}""",
            """{"login":"my-lector[bot]","id":{},"type":"Bot"}""",
            "[]",
            "\"<html>\"",
        ).forEach { body ->
            answer = 200 to body

            val error = assertFailsWith<ToolError>(body) { gitHubAppIdentity(apiUrl(), "ghs_token", "my-lector") }

            assertEquals("GitHub's user my-lector[bot] is not the bot of the GitHub App my-lector: $body", error.message, body)
        }
    }

    @Test
    fun `an error status, an answer that isn't JSON, and an unreachable API are tool errors that name the slug`() {
        answer = 404 to """{"message":"Not Found"}"""
        val missing = assertFailsWith<ToolError> { gitHubAppIdentity(apiUrl(), "ghs_token", "my-lector") }
        assertEquals(
            "Can't look up the bot of the GitHub App my-lector: " +
                "GitHub answered GET /users/my-lector%5Bbot%5D with status 404: {\"message\":\"Not Found\"}",
            missing.message,
        )

        answer = 200 to "{"
        val html = assertFailsWith<ToolError> { gitHubAppIdentity(apiUrl(), "ghs_token", "my-lector") }
        assertContains(html.message, "Can't look up the bot of the GitHub App my-lector: GitHub's answer is not JSON")

        val unreachable = assertFailsWith<ToolError> { gitHubAppIdentity(URI("http://127.0.0.1:1"), "ghs_token", "my-lector") }
        assertContains(unreachable.message, "Can't look up the bot of the GitHub App my-lector: Cannot reach GitHub at http://127.0.0.1:1")
    }

    private fun apiUrl() = URI("http://127.0.0.1:${server.address.port}")
}
