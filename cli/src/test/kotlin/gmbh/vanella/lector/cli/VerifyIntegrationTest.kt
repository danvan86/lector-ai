package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import gmbh.vanella.lector.core.runProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * Runs `verify` with real Maven on a repository with a base and a head commit. The head updates commons-lang3, which
 * breaks one test. Another test fails on both commits. Needs Maven and network access, so it is opt-in:
 * `./gradlew :cli:integrationTest`.
 */
@Tag("integration")
class VerifyIntegrationTest {
    @TempDir
    lateinit var repo: Path

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    fun `finds the failure caused by the update and the pre-existing one`() {
        commit("base", "base: commons-lang3 3.12.0")
        commit("head", "head: update commons-lang3 to 3.18.0")

        val result = rootCommand("test").test("verify $repo --base HEAD~1 --format json")

        val verification = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(listOf("com.example.ReleasesTest#knowsReleasesUpToJava17"), tests(verification, "causedByUpdate"))
        assertEquals(listOf("com.example.ReleasesTest#addsWrong"), tests(verification, "preExisting"))
        assertEquals(emptyList(), tests(verification, "flaky"))
        assertEquals(emptyList(), tests(verification, "unattributed"))
        assertEquals(1, result.statusCode)
        assertEquals(1, git("worktree", "list").lines().count { it.isNotBlank() }, "the base worktree is removed")
        assertEquals("", git("status", "--porcelain"), "verify changes nothing outside ignored build output")
    }

    private fun tests(
        verification: Map<String, kotlinx.serialization.json.JsonElement>,
        category: String,
    ) = verification.getValue(category).jsonArray.map {
        it.jsonObject
            .getValue("test")
            .jsonPrimitive.content
    }

    // Copies the fixture's files for this commit over the repository and commits them.
    @OptIn(ExperimentalPathApi::class)
    private fun commit(
        files: String,
        message: String,
    ) {
        val fixture = requireNotNull(javaClass.getResource("/fixtures/dependency-update/$files")).toURI().toPath()
        fixture.copyToRecursively(repo, followLinks = false, overwrite = true)
        if (files == "base") {
            git("init", "--quiet")
            // Gradle leaves dotfiles out of test resources, so the fixture can't carry its own .gitignore.
            repo.resolve(".gitignore").writeText("target/\n")
        }
        git("add", ".")
        git("commit", "--quiet", "-m", message)
    }

    private fun git(vararg args: String): String {
        val identity = listOf("-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
        val result = runProcess(listOf("git") + identity + args, repo, 1.minutes)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }
}
