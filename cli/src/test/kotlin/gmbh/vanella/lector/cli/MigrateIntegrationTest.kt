package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import kotlinx.serialization.json.Json
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
 * Runs `migrate` with real Maven on a Spring Boot 3.3 → 3.4 update: the head commit only bumps the parent, like a bot
 * PR. The code calls `RestTemplateBuilder.setConnectTimeout`, which `UpgradeSpringBoot_3_4` renames by type. If that
 * rename is missing, running the recipe on the PR head loses type information (brainstorming Q8). Needs Maven and
 * network access, so it is opt-in: `./gradlew :cli:integrationTest`.
 */
@Tag("integration")
class MigrateIntegrationTest {
    @TempDir
    lateinit var repo: Path

    private val fixture by lazy { FixtureRepository(repo, "spring-boot-upgrade") }

    @Test
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    fun `applies UpgradeSpringBoot_3_4 to the PR head, then verifies`() {
        fixture.commit("base", "base: Spring Boot 3.3.5")
        fixture.commit("head", "head: update Spring Boot to 3.4.1")
        val parent = "org.springframework.boot:spring-boot-starter-parent"

        val result =
            rootCommand("test").test("migrate $repo --package $parent --from 3.3.5 --to 3.4.1 --base HEAD~1 --format json")

        val json = Json.parseToJsonElement(result.stdout).jsonObject
        val recipe = json.getValue("recipe").jsonObject
        assertEquals("applied", recipe.text("outcome"), result.stdout)
        assertEquals("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4", recipe.getValue("chosen").jsonObject.text("name"))
        assertEquals(
            listOf("pom.xml", "src/main/java/com/example/Clients.java"),
            recipe.getValue("changedFiles").jsonArray.map { it.jsonPrimitive.content },
        )

        val clients = repo.resolve("src/main/java/com/example/Clients.java").readText()
        assertContains(clients, "builder.connectTimeout(Duration.ofSeconds(5))", message = "the type-based rename happened")

        val parentChange =
            recipe
                .getValue("versionChanges")
                .jsonArray
                .map { it.jsonObject }
                .single { it.text("package") == parent }
        assertEquals("3.4.1", parentChange.text("before"))
        assertTrue(parentChange.text("after").startsWith("3.4."), "the recipe keeps the parent on 3.4.x: $parentChange")

        val verification = json.getValue("verification").jsonObject
        assertEquals(emptyList(), verification.getValue("causedByUpdate").jsonArray.toList())
        assertEquals(0, result.statusCode, result.stderr)

        assertFalse("rewrite-maven-plugin" in repo.resolve("pom.xml").readText(), "the plugin is called by coordinates only")
        assertEquals("2", fixture.git("rev-list", "--count", "HEAD").trim(), "the recipe's changes are not committed")
        assertContains(fixture.git("status", "--porcelain"), " M pom.xml")
        assertEquals(1, fixture.git("worktree", "list").lines().count { it.isNotBlank() }, "the base worktree is removed")
    }

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
}
