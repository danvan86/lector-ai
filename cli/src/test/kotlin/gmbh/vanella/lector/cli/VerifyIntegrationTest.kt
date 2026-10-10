package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs `verify` with real Maven on a repository with a base and a head commit. The head updates commons-lang3, which
 * breaks one test. Another test fails on both commits. Needs Maven and network access, so it is opt-in:
 * `./gradlew :cli:integrationTest`.
 */
@Tag("integration")
class VerifyIntegrationTest {
    @TempDir
    lateinit var repo: Path

    private val fixture by lazy { FixtureRepository(repo, "dependency-update") }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    fun `finds the failure caused by the update and the pre-existing one`() {
        fixture.commit("base", "base: commons-lang3 3.12.0")
        fixture.commit("head", "head: update commons-lang3 to 3.18.0")

        val result = rootCommand("test").test("verify $repo --base HEAD~1 --format json")

        val verification = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(listOf("com.example.ReleasesTest#knowsReleasesUpToJava17"), tests(verification, "causedByUpdate"))
        assertEquals(listOf("com.example.ReleasesTest#addsWrong"), tests(verification, "preExisting"))
        assertEquals(emptyList(), tests(verification, "flaky"))
        assertEquals(emptyList(), tests(verification, "unattributed"))
        assertEquals(1, result.statusCode)
        assertEquals(1, fixture.git("worktree", "list").lines().count { it.isNotBlank() }, "the base worktree is removed")
        assertEquals("", fixture.git("status", "--porcelain"), "verify changes nothing outside ignored build output")
    }

    private fun tests(
        verification: Map<String, kotlinx.serialization.json.JsonElement>,
        category: String,
    ) = verification.getValue(category).jsonArray.map {
        it.jsonObject
            .getValue("test")
            .jsonPrimitive.content
    }
}
