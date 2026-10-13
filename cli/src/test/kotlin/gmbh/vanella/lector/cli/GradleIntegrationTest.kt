package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `verify` and `migrate` with real Gradle on repositories with a base and a head commit, like a bot PR. Needs network
 * access, so it is opt-in: `./gradlew :cli:integrationTest`. The task provides JDKs 21 and 25, a Gradle user home of its
 * own, and the OpenRewrite Gradle plugin, as the tool image does.
 */
@Tag("integration")
class GradleIntegrationTest {
    @TempDir
    lateinit var repo: Path

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    fun `verify finds the failure caused by the update and the pre-existing one`() {
        val fixture = FixtureRepository(repo, "gradle-dependency-update")
        fixture.commit("base", "base: commons-lang3 3.12.0")
        fixture.commit("head", "head: update commons-lang3 to 3.18.0")

        val result = rootCommand("test").test("verify $repo --base HEAD~1 --format json")

        val verification = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(listOf("com.example.ReleasesTest#knowsReleasesUpToJava17()"), tests(verification, "causedByUpdate"))
        assertEquals(listOf("com.example.ReleasesTest#addsWrong()"), tests(verification, "preExisting"))
        assertEquals(emptyList(), tests(verification, "flaky"))
        assertEquals(emptyList(), tests(verification, "unattributed"))
        assertEquals(1, result.statusCode, result.stderr)
        assertEquals(1, fixture.git("worktree", "list").lines().count { it.isNotBlank() }, "the base worktree is removed")
        assertEquals("", fixture.git("status", "--porcelain"), "verify changes nothing outside ignored build output")
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    fun `migrate applies UpgradeSpringBoot_3_4 for the Spring Boot plugin, by type, on the PR head, then verifies`() {
        val fixture = FixtureRepository(repo, "gradle-spring-boot-upgrade")
        fixture.commit("base", "base: Spring Boot 3.3.5")
        fixture.commit("head", "head: update the Spring Boot plugin to 3.4.1")
        val plugin = "org.springframework.boot:org.springframework.boot.gradle.plugin"

        val result = rootCommand("test").test("migrate $repo --package $plugin --from 3.3.5 --to 3.4.1 --base HEAD~1 --format json")

        val json = Json.parseToJsonElement(result.stdout).jsonObject
        val recipe = json.getValue("recipe").jsonObject
        assertEquals("applied", recipe.text("outcome"), result.stdout + result.stderr)
        assertEquals("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4", recipe.getValue("chosen").jsonObject.text("name"))
        val clients = repo.resolve("src/main/java/com/example/Clients.java").readText()
        assertContains(clients, "builder.connectTimeout(Duration.ofSeconds(5))", message = "the type-based rename happened")

        val pluginChange =
            recipe
                .getValue("versionChanges")
                .jsonArray
                .map { it.jsonObject }
                .single { it.text("package") == plugin }
        assertEquals("3.4.1", pluginChange.text("before"))
        assertTrue(pluginChange.text("after").startsWith("3.4."), "the recipe keeps the plugin on 3.4.x: $pluginChange")

        val verification = json.getValue("verification").jsonObject
        assertEquals(emptyList(), verification.getValue("causedByUpdate").jsonArray.toList())
        assertEquals(0, result.statusCode, result.stderr)

        // The recipe's own changes, and nothing to apply the plugin or run the recipe.
        val changed = recipe.getValue("changedFiles").jsonArray.map { it.jsonPrimitive.content }
        assertTrue("src/main/java/com/example/Clients.java" in changed, "$changed")
        assertEquals(
            changed.sorted(),
            fixture
                .git("diff", "--name-only")
                .lines()
                .filter { it.isNotBlank() }
                .sorted(),
        )
        val build = repo.resolve("build.gradle").readText() + repo.resolve("settings.gradle").readText()
        assertFalse("rewrite" in build || "lector" in build, build)
        assertEquals(1, fixture.git("worktree", "list").lines().count { it.isNotBlank() }, "the base worktree is removed")
    }

    private fun tests(
        verification: Map<String, JsonElement>,
        category: String,
    ) = verification.getValue(category).jsonArray.map { it.jsonObject.text("test") }

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
}
