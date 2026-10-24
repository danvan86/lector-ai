package gmbh.vanella.lector.cli

import com.sun.net.httpserver.HttpServer
import gmbh.vanella.lector.core.runProcess
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * M10, through the installed start script as the action runs it: the identity from the App's slug, with the action's
 * empty bot options, and the `gitIgnoredAuthors` value in the log of every run on a Renovate PR, and only there.
 */
@Tag("e2e")
class SimpleSetupEndToEndTest {
    @Test
    fun `pr with the action's arguments looks the bot up and logs the value for Renovate's gitIgnoredAuthors on a green PR`(
        @TempDir repo: Path,
    ) {
        val head = mavenProject(repo, authorEmail = "dev@example.com")
        val event = event(author = "renovate[bot]", branch = "renovate/spring-boot", head = head)
        val requests = mutableListOf<String>()
        val github = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        github.createContext("/") { exchange ->
            requests += "${exchange.requestMethod} ${exchange.requestURI.path} ${exchange.requestHeaders.getFirst("Authorization")}"
            val answer =
                if (exchange.requestURI.path.startsWith("/users/")) {
                    """{"login": "my-lector[bot]", "id": 123, "type": "Bot"}"""
                } else {
                    "[]"
                }.encodeToByteArray()
            exchange.sendResponseHeaders(200, answer.size.toLong())
            exchange.responseBody.use { it.write(answer) }
        }
        github.start()

        val run =
            try {
                lector(
                    "pr",
                    repo.toString(),
                    "--app-slug=my-lector",
                    "--bot-name=",
                    "--bot-email=",
                    environment =
                        mapOf(
                            "GITHUB_EVENT_PATH" to event.toString(),
                            "GITHUB_API_URL" to "http://127.0.0.1:${github.address.port}",
                            "LECTOR_GITHUB_TOKEN" to "ghs_token",
                        ),
                )
            } finally {
                github.stop(0)
            }

        assertEquals(0, run.exitCode, run.stderr)
        assertContains(run.stdout, "Verify is green: nothing caused by the update. lector-ai changed nothing.\n")
        assertEquals("GET /users/my-lector[bot] Bearer ghs_token", requests.first())
        assertTrue(
            run.stderr.lines().any { "gitIgnoredAuthors" in it && "123+my-lector[bot]@users.noreply.github.com" in it },
            "one log line names the email for gitIgnoredAuthors:\n${run.stderr}",
        )
    }

    @Test
    fun `a run on a Renovate PR whose head is the tool's own commit still logs the value for gitIgnoredAuthors`(
        @TempDir repo: Path,
    ) {
        val head = mavenProject(repo, authorEmail = EMAIL)

        val run = skipOwnCommit(repo, event(author = "renovate[bot]", branch = "renovate/spring-boot", head = head))

        assertTrue(
            run.stderr.lines().any { "gitIgnoredAuthors" in it && EMAIL in it },
            "one log line names the email for gitIgnoredAuthors:\n${run.stderr}",
        )
    }

    @Test
    fun `runs on a Dependabot PR and on a labelled PR don't mention gitIgnoredAuthors`(
        @TempDir dependabotRepo: Path,
        @TempDir labelledRepo: Path,
    ) {
        val dependabotHead = mavenProject(dependabotRepo, authorEmail = EMAIL)
        val labelledHead = mavenProject(labelledRepo, authorEmail = EMAIL)

        val runs =
            listOf(
                skipOwnCommit(dependabotRepo, event("dependabot[bot]", "dependabot/maven/boot", dependabotHead)),
                skipOwnCommit(labelledRepo, event("alice", "bump-boot", labelledHead, labels = listOf("lector"))),
            )

        for (run in runs) {
            assertTrue("gitIgnoredAuthors" !in run.stderr, run.stderr)
        }
    }

    /** Runs `pr` with the typed-in pair on a PR whose head commit is the tool's own, and checks that it was skipped. */
    private fun skipOwnCommit(
        repo: Path,
        event: Path,
    ): Run {
        val run =
            lector(
                "pr",
                repo.toString(),
                "--bot-name",
                "lector-ai[bot]",
                "--bot-email",
                EMAIL,
                environment = mapOf("GITHUB_EVENT_PATH" to event.toString(), "LECTOR_GITHUB_TOKEN" to "t"),
            )
        assertEquals("Skipped: the head commit is the tool's own\n", run.stdout, run.stderr)
        assertEquals(0, run.exitCode, run.stderr)
        return run
    }

    /** A `pull_request` event for PR 7 of acme/app whose base and head are [head], in a temporary file outside the repository. */
    private fun event(
        author: String,
        branch: String,
        head: String,
        labels: List<String> = emptyList(),
    ): Path {
        val body = "This PR contains the following updates:\n\n| Package | Change |\n|---|---|\n| $PARENT | `3.3.5` → `3.4.1` |"
        val commit = { sha: String ->
            buildJsonObject {
                put("ref", branch)
                put("sha", sha)
                put("repo", buildJsonObject { put("full_name", "acme/app") })
            }
        }
        val event =
            buildJsonObject {
                put(
                    "pull_request",
                    buildJsonObject {
                        put("number", 7)
                        put("state", "open")
                        put("merged", false)
                        put("labels", buildJsonArray { labels.forEach { add(buildJsonObject { put("name", it) }) } })
                        put("body", body)
                        put("user", buildJsonObject { put("login", author) })
                        put("head", commit(head))
                        put("base", commit(head))
                    },
                )
            }
        val file = File.createTempFile("event", ".json").apply { deleteOnExit() }.toPath()
        file.writeText(event.toString())
        return file
    }

    /**
     * A committed, green Maven project whose head commit is by [authorEmail]. Its wrapper stands in for Maven and writes
     * an effective POM with the Spring Boot parent where `help:effective-pom` asks for one. Returns the head commit.
     */
    private fun mavenProject(
        repo: Path,
        authorEmail: String,
    ): String {
        repo.resolve("pom.xml").writeText("<project/>\n")
        val wrapper = repo.resolve("mvnw")
        wrapper.writeText(
            """
            #!/bin/sh
            echo "${'$'}*" >> "${'$'}(dirname "${'$'}0")/mvn.log"
            for arg in "${'$'}@"; do
              case "${'$'}arg" in
                -Doutput=*) echo '<project><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.4.1</version></parent></project>' > "${'$'}{arg#-Doutput=}" ;;
              esac
            done
            exit 0
            """.trimIndent() + "\n",
        )
        wrapper.toFile().setExecutable(true)
        repo.resolve(".gitignore").writeText("mvn.log\n")
        git(repo, "init", "--quiet")
        git(repo, "add", ".")
        git(
            repo,
            "-c",
            "user.name=Someone",
            "-c",
            "user.email=$authorEmail",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "--quiet",
            "-m",
            "head",
        )
        return git(repo, "rev-parse", "HEAD")
    }

    private fun git(
        repo: Path,
        vararg args: String,
    ): String {
        val result = runProcess(listOf("git") + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output.trim()
    }

    private data class Run(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    // Both streams go to files, so a full pipe buffer can't block the process. The timeout keeps a hanging run from blocking the build.
    private fun lector(
        vararg args: String,
        environment: Map<String, String> = emptyMap(),
    ): Run {
        val stdout = File.createTempFile("lector", ".out").apply { deleteOnExit() }
        val stderr = File.createTempFile("lector", ".err").apply { deleteOnExit() }
        val process =
            ProcessBuilder(System.getProperty("lector.bin"), *args)
                .redirectOutput(stdout)
                .redirectError(stderr)
                .apply {
                    environment()["JAVA_HOME"] = System.getProperty("java.home")
                    environment().putAll(environment)
                }.start()
        val exited = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!exited) process.destroyForcibly()
        assertTrue(exited, "lector did not exit within $TIMEOUT_SECONDS seconds")
        return Run(process.exitValue(), stdout.readText(), stderr.readText())
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
        const val PARENT = "org.springframework.boot:spring-boot-starter-parent"
        const val EMAIL = "1+lector-ai[bot]@users.noreply.github.com"
    }
}
