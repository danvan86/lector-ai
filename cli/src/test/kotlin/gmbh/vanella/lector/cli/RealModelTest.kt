package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.ollama.OllamaContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The M3 "done when": the head commit updates Guava 18.0 to 21.0, which removed `Objects.firstNonNull`, and no recipe
 * matches. `heal` must turn it green through the fix loop alone, with a real model in Ollama. Needs Docker, Maven, and
 * network access, and takes minutes, so it is opt-in: `./gradlew :cli:realModelTest [-Plector.model=<name>]`.
 */
@Tag("real-model")
class RealModelTest {
    @TempDir
    lateinit var repo: Path

    private val model = System.getProperty("lector.model") ?: DEFAULT_MODEL
    private val image = requireNotNull(System.getProperty("lector.ollamaImage")) { "Run with ./gradlew :cli:realModelTest" }
    private val fixture by lazy { FixtureRepository(repo, "guava-update") }

    @Test
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    fun `a Guava update without a recipe goes from red to green through the fix loop alone`() {
        fixture.commit("base", "base: Guava 18.0")
        fixture.commit("head", "head: update Guava to 21.0")
        val head = fixture.git("rev-parse", "HEAD").trim()
        val test = repo.resolve("src/test/java/com/example/GreetingTest.java").readText()

        val result =
            OllamaContainer(DockerImageName.parse(image)).use { ollama ->
                ollama.start()
                val pull = ollama.execInContainer("ollama", "pull", model)
                assertEquals(0, pull.exitCode, pull.stderr)
                val args = "--package com.google.guava:guava --from 18.0 --to 21.0 --base HEAD~1"
                rootCommand("test").test("heal $repo $args --ollama-url ${ollama.endpoint} --model $model --format json")
            }

        assertTrue(result.stdout.isNotBlank(), "heal ended with exit ${result.statusCode}: ${result.stderr}")
        val json = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(
            "noMatch",
            json
                .getValue("recipe")
                .jsonObject
                .getValue("outcome")
                .jsonPrimitive.content,
            "no recipe ran",
        )
        assertEquals(
            emptyList(),
            json
                .getValue("verification")
                .jsonObject
                .getValue("causedByUpdate")
                .jsonArray
                .toList(),
            result.stdout,
        )
        assertEquals(0, result.statusCode, result.stderr)
        val fixes = fixture.git("log", "--format=%an", "$head..HEAD").lines().filter { it.isNotBlank() }
        assertTrue(fixes.isNotEmpty() && fixes.all { it == "lector" }, "one commit per fix, by lector: $fixes")
        assertEquals("", fixture.git("status", "--porcelain"))
        assertEquals(test, repo.resolve("src/test/java/com/example/GreetingTest.java").readText(), "the tests are untouched")
        assertContains(repo.resolve("pom.xml").readText(), "<version>21.0</version>", message = "the update stays")
    }
}
