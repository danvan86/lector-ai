package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * M11 section 3 with real Maven: the fixture signs its artifacts in `verify` with a `gpg` that doesn't exist, so a build
 * that signs fails wherever it runs. lector's verify skips signing and is green. Opt-in: `./gradlew :cli:integrationTest`.
 */
@Tag("integration")
class SigningIntegrationTest {
    @TempDir
    lateinit var repo: Path

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    fun `a project that signs in verify verifies green, because lector's builds don't sign`() {
        FixtureRepository(repo, "signed-build").commit("head", "a project that signs its artifacts")

        val result = rootCommand("test").test("verify $repo --format json")

        val verification = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(emptyList(), verification.getValue("causedByUpdate").jsonArray.toList(), result.stdout)
        assertEquals(0, result.statusCode, result.stderr)
    }
}
