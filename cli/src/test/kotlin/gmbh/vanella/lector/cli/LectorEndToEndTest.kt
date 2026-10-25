package gmbh.vanella.lector.cli

import com.sun.net.httpserver.HttpServer
import gmbh.vanella.lector.core.runProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Runs the installed `lector` start script as a separate process, the way the pipeline calls it. */
@Tag("e2e")
class LectorEndToEndTest {
    private val version = System.getProperty("lector.version")

    @Test
    fun `--version prints the version to stdout and exits with 0`() {
        val run = lector("--version")

        assertEquals("$version\n", run.stdout)
        assertEquals("", run.stderr)
        assertEquals(0, run.exitCode)
    }

    @Test
    fun `no arguments prints the help to stdout and exits with 0`() {
        val run = lector()

        assertContains(run.stdout, "Usage: lector")
        assertContains(run.stdout, "--version")
        assertEquals("", run.stderr)
        assertEquals(0, run.exitCode)
    }

    @Test
    fun `unknown option prints the error to stderr and exits with 2`() {
        val run = lector("--bogus")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "no such option --bogus")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `verify with invalid usage exits with 2`(
        @TempDir repo: Path,
    ) {
        val run = lector("verify", repo.toString(), "--format", "xml")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "invalid value for --format")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `logs go to stderr and keep stdout clean`() {
        val run = lector("--version", javaOpts = "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug")

        assertEquals("$version\n", run.stdout)
        assertContains(run.stderr, "Starting lector $version")
    }

    @Test
    fun `verify of a repository without a supported ecosystem exits with 2`(
        @TempDir repo: Path,
    ) {
        val run = lector("verify", repo.toString())

        assertEquals("", run.stdout)
        assertEquals("Error: No supported ecosystem in $repo. $SUPPORTED\n", run.stderr)
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `verify with an invalid base ref exits with 2 before Maven runs`(
        @TempDir repo: Path,
    ) {
        repo.resolve("pom.xml").createFile()

        val run = lector("verify", repo.toString(), "--base", "no-such-ref", "--format", "json")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `help lists exactly the verify, migrate, heal, and pr commands`() {
        val run = lector("--help")

        val commands =
            run.stdout
                .substringAfter("Commands:")
                .lines()
                .filter { it.startsWith("  ") && !it.startsWith("   ") }
        assertEquals(listOf("verify", "migrate", "heal", "pr"), commands.map { it.trim().substringBefore(" ") })
    }

    @Test
    fun `migrate without --package exits with 2`(
        @TempDir repo: Path,
    ) {
        val run = lector("migrate", repo.toString(), "--from", "3.3.5", "--to", "3.4.1")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "missing option --package")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `migrate of a repository with uncommitted changes exits with 2`(
        @TempDir repo: Path,
    ) {
        git(repo, "init", "--quiet")
        repo.resolve("pom.xml").createFile()

        val run = lector("migrate", repo.toString(), "--package", PARENT, "--from", "3.3.5", "--to", "3.4.1")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "has uncommitted changes. Commit or stash them first: pom.xml")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `migrate finds the recipe in the bundled index and runs it with the project's Maven`(
        @TempDir repo: Path,
    ) {
        mavenProject(repo)

        val run = lector("migrate", repo.toString(), "--package", PARENT, "--from", "3.3.5", "--to", "3.4.1")

        assertContains(run.stdout, "Recipe (applied)\n")
        assertContains(run.stdout, "  Recipe: org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4, leaf 3.4.x at depth 1\n")
        assertContains(run.stdout, "  Artifact: org.openrewrite.recipe:rewrite-spring:")
        assertContains(run.stdout, "Caused by the update (0)")
        assertEquals(0, run.exitCode, run.stderr)
        assertContains(repo.resolve("mvn.log").readText(), "org.openrewrite.maven:rewrite-maven-plugin:")
    }

    @Test
    fun `migrate --format json prints one JSON object with the recipe section and the verification`(
        @TempDir repo: Path,
    ) {
        mavenProject(repo)

        val run =
            lector("migrate", repo.toString(), "--package", "com.example:no-recipe", "--from", "1.0", "--to", "2.0", "--format", "json")

        val json = Json.parseToJsonElement(run.stdout).jsonObject
        assertEquals(listOf("recipe", "verification"), json.keys.toList())
        assertEquals(
            "noMatch",
            json
                .getValue("recipe")
                .jsonObject
                .getValue("outcome")
                .jsonPrimitive.content,
        )
        assertEquals(0, run.exitCode, run.stderr)
    }

    @Test
    fun `heal without --package exits with 2`(
        @TempDir repo: Path,
    ) {
        val run = lector("heal", repo.toString(), "--from", "3.3.5", "--to", "3.4.1")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "missing option --package")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `heal of a green project prints one JSON object without asking the model, and exits with 0`(
        @TempDir repo: Path,
    ) {
        mavenProject(repo)

        val run =
            lector("heal", repo.toString(), "--package", "com.example:no-recipe", "--from", "1.0", "--to", "2.0", "--format", "json")

        val json = Json.parseToJsonElement(run.stdout).jsonObject
        assertEquals(listOf("recipe", "recipeCommit", "fixLoop", "verification"), json.keys.toList())
        assertEquals(
            "nothingLeft",
            json
                .getValue("fixLoop")
                .jsonObject
                .getValue("stopReason")
                .jsonPrimitive.content,
        )
        assertEquals(0, run.exitCode, run.stderr)
    }

    @Test
    fun `heal with an unreachable Ollama exits with 2`(
        @TempDir repo: Path,
    ) {
        mavenProject(repo, output = "[ERROR] $repo/src/main/java/App.java:[3,9] cannot find symbol", exitCode = 1)
        val port = ServerSocket(0).use { it.localPort }

        val run =
            lector(
                "heal",
                repo.toString(),
                "--package",
                "com.example:no-recipe",
                "--from",
                "1.0",
                "--to",
                "2.0",
                "--ollama-url",
                "http://127.0.0.1:$port",
            )

        assertEquals("", run.stdout)
        assertContains(run.stderr, "Error: Ollama is not reachable at http://127.0.0.1:$port")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `heal rejects an edit that breaks a guardrail, parsing Java with the bundled tree-sitter grammar`(
        @TempDir repo: Path,
    ) {
        repo.resolve("src/main/java").createDirectories()
        repo.resolve("src/main/java/App.java").writeText("class App {\n    Object run() {\n        broken();\n    }\n}\n")
        mavenProject(repo, output = "[ERROR] $repo/src/main/java/App.java:[3,9] cannot find symbol", exitCode = 1)
        val edit = """{"file":"src/main/java/App.java","search":"broken();","replace":"return null;"}"""
        val ollama = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        ollama.createContext("/") { exchange ->
            exchange.requestBody.readAllBytes()
            val content = """{"analysis":"a","rationale":"Stub it","edits":[$edit]}"""
            val answer = buildJsonObject { put("message", buildJsonObject { put("content", content) }) }.toString().encodeToByteArray()
            exchange.sendResponseHeaders(200, answer.size.toLong())
            exchange.responseBody.use { it.write(answer) }
        }
        ollama.start()

        val run =
            try {
                lector(
                    "heal",
                    repo.toString(),
                    "--package",
                    "com.example:no-recipe",
                    "--from",
                    "1.0",
                    "--to",
                    "2.0",
                    "--ollama-url",
                    "http://127.0.0.1:${ollama.address.port}",
                )
            } finally {
                ollama.stop(0)
            }

        assertContains(run.stdout, "- [stubbing] src/main/java/App.java:3: the edits add the placeholder `return null;` in place of logic")
        // The fake model gives the same answer every time: attempt 2 asks twice and builds nothing.
        assertContains(run.stdout, "rejected: the first answer repeated attempt 1, and the second repeated attempt 1; neither was built")
        assertContains(run.stdout, "  Rejections per guardrail (1):\n    - stubbing: 1\n")
        // The rejected edits are in the log: without them, a failed run in CI can't be told from a bad prompt.
        assertContains(run.stderr, "The rejected edits:\n- src/main/java/App.java: \"broken();\" -> \"return null;\"\n")
        assertTrue("WARNING" !in run.stderr, run.stderr)
        assertEquals(1, run.exitCode, run.stderr)
    }

    @Test
    fun `pr skips a PR that isn't the bot's and exits with 0`(
        @TempDir repo: Path,
    ) {
        val event = event(author = "mallory", head = "1111111", base = "2222222")

        val run =
            lector(
                "pr",
                repo.toString(),
                *BOT,
                environment =
                    mapOf(
                        "GITHUB_EVENT_PATH" to event.toString(),
                        "LECTOR_GITHUB_TOKEN" to "t",
                    ),
            )

        assertEquals("Skipped: no update source recognizes the PR by mallory on renovate/spring-boot\n", run.stdout)
        assertContains(run.stderr, "Skipping the PR: no update source recognizes the PR by mallory")
        assertEquals(0, run.exitCode)
    }

    @Test
    fun `pr with Dependabot metadata that isn't fetch-metadata's JSON exits with 2 before anything runs`(
        @TempDir repo: Path,
    ) {
        val event = event(author = "dependabot[bot]", head = "1111111", base = "2222222")

        val run =
            lector(
                "pr",
                repo.toString(),
                *BOT,
                environment =
                    mapOf(
                        "GITHUB_EVENT_PATH" to event.toString(),
                        "LECTOR_GITHUB_TOKEN" to "t",
                        "LECTOR_DEPENDABOT_METADATA" to """{"dependencyName": "a:b"}""",
                    ),
            )

        assertEquals("", run.stdout)
        assertContains(
            run.stderr,
            "invalid value for --dependabot-metadata: The Dependabot metadata is not fetch-metadata's updated-dependencies-json: not a JSON array",
        )
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `pr of a closed PR without a companion PR looks for one on GitHub, deletes a companion branch left over, and exits with 0`(
        @TempDir repo: Path,
    ) {
        val requests = mutableListOf<String>()
        val github = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        github.createContext("/") { exchange ->
            requests += "${exchange.requestMethod} ${exchange.requestURI.path}"
            val answer = "[]".encodeToByteArray()
            exchange.sendResponseHeaders(200, answer.size.toLong())
            exchange.responseBody.use { it.write(answer) }
        }
        github.start()
        val event = event(author = "dependabot[bot]", head = "1111111", base = "2222222", state = "closed")

        val run =
            try {
                lector(
                    "pr",
                    repo.toString(),
                    *BOT,
                    environment =
                        mapOf(
                            "GITHUB_EVENT_PATH" to event.toString(),
                            "GITHUB_API_URL" to "http://127.0.0.1:${github.address.port}",
                            "LECTOR_GITHUB_TOKEN" to "t",
                        ),
                )
            } finally {
                github.stop(0)
            }

        assertEquals("The PR is closed. It has no companion PR.\n", run.stdout)
        assertEquals(listOf("GET /repos/acme/app/pulls", "DELETE /repos/acme/app/git/refs/heads/heal/renovate/spring-boot"), requests)
        assertEquals(0, run.exitCode, run.stderr)
    }

    @Test
    fun `pr without the token exits with 2`(
        @TempDir repo: Path,
    ) {
        val event = event(author = "renovate[bot]", head = "1111111", base = "2222222")

        val run = lector("pr", repo.toString(), "--event", event.toString(), *BOT)

        assertEquals("", run.stdout)
        assertEquals("Error: Set LECTOR_GITHUB_TOKEN to the GitHub App's installation token\n", run.stderr)
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `pr of a green Renovate PR verifies it, looks for its companion PR and comment on GitHub, changes nothing, and exits with 0`(
        @TempDir repo: Path,
    ) {
        mavenProject(repo)
        val head = runProcess(listOf("git", "rev-parse", "HEAD"), repo, 30.seconds).output.trim()
        val event = event(author = "renovate[bot]", head = head, base = head)
        val requests = mutableListOf<String>()
        val github = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        github.createContext("/") { exchange ->
            requests += "${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestHeaders.getFirst("Authorization")}"
            val answer = "[]".encodeToByteArray()
            exchange.sendResponseHeaders(200, answer.size.toLong())
            exchange.responseBody.use { it.write(answer) }
        }
        github.start()

        val run =
            try {
                lector(
                    "pr",
                    repo.toString(),
                    *BOT,
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

        assertContains(run.stdout, "Verify is green: nothing caused by the update. lector-ai changed nothing.\n")
        assertEquals(
            listOf(
                "GET /repos/acme/app/pulls?state=open&head=acme%3Aheal%2Frenovate%2Fspring-boot&base=renovate%2Fspring-boot&per_page=100 " +
                    "Bearer ghs_token",
                "GET /repos/acme/app/issues/7/comments?page=1&per_page=100 Bearer ghs_token",
            ),
            requests,
        )
        assertEquals(head, runProcess(listOf("git", "rev-parse", "HEAD"), repo, 30.seconds).output.trim())
        assertEquals(0, run.exitCode, run.stderr)
    }

    /** A `pull_request` event for PR 7 of acme/app, on the Renovate branch, in a temporary file outside the repository. */
    private fun event(
        author: String,
        head: String,
        base: String,
        state: String = "open",
    ): Path {
        val body = "This PR contains the following updates:\n\n| Package | Change |\n|---|---|\n| $PARENT | `3.3.5` → `3.4.1` |"
        val event =
            buildJsonObject {
                put(
                    "pull_request",
                    buildJsonObject {
                        put("number", 7)
                        put("state", state)
                        put("merged", false)
                        put("labels", buildJsonArray {})
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
                    },
                )
            }
        val file = File.createTempFile("event", ".json").apply { deleteOnExit() }.toPath()
        file.writeText(event.toString())
        return file
    }

    /**
     * A committed Maven project whose wrapper stands in for Maven: it logs its arguments, writes an effective POM with
     * the Spring Boot parent where `help:effective-pom` asks for one, prints [output], and exits with [exitCode].
     */
    private fun mavenProject(
        repo: Path,
        output: String = "",
        exitCode: Int = 0,
    ) {
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
            echo '$output'
            exit $exitCode
            """.trimIndent() + "\n",
        )
        wrapper.toFile().setExecutable(true)
        repo.resolve(".gitignore").writeText("mvn.log\n")
        git(repo, "init", "--quiet")
        git(repo, "add", ".")
        git(
            repo,
            "-c",
            "user.name=Lector",
            "-c",
            "user.email=lector@example.com",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "--quiet",
            "-m",
            "head",
        )
    }

    private fun git(
        repo: Path,
        vararg args: String,
    ) {
        val result = runProcess(listOf("git") + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
    }

    private data class Run(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    // Both streams go to files, so a full pipe buffer can't block the process. The timeout keeps a hanging run from blocking the build.
    private fun lector(
        vararg args: String,
        javaOpts: String = "",
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
                    environment()["JAVA_OPTS"] = javaOpts
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
        val BOT = arrayOf("--bot-name", "lector-ai[bot]", "--bot-email", "1+lector-ai[bot]@users.noreply.github.com")
    }
}
