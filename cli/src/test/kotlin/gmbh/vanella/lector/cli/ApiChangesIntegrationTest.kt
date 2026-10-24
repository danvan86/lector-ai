package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.UpdateType
import gmbh.vanella.lector.core.runProcess
import gmbh.vanella.lector.ecosystems.jvm.MavenAdapter
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The API changes of the Guava 18.0 → 21.0 update with real Maven, on a runner that has never seen Guava 18.0: verify
 * resolves 21.0, and the API changes fetch 18.0 with the dependency plugin, without writing into the repository. Maven
 * uses a local repository of its own here. Needs Maven and network access, so it is opt-in: `./gradlew :cli:integrationTest`.
 */
@Tag("integration")
class ApiChangesIntegrationTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var localRepository: Path

    private val fixture by lazy { FixtureRepository(repo, "guava-update") }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    fun `the old version is fetched once and the moved method is named`() {
        fixture.commit("base", "base: Guava 18.0")
        fixture.commit("head", "head: update Guava to 21.0")
        val commands = mutableListOf<List<String>>()
        val adapter =
            MavenAdapter(localRepository = localRepository) { command, workDir, environment, timeout ->
                commands += command
                runProcess(command, workDir, timeout, environment + ("MAVEN_OPTS" to "-Dmaven.repo.local=$localRepository"))
            }
        val change = DependencyChange("maven", "com.google.guava:guava", "18.0", "21.0", UpdateType.MAJOR)

        val diagnostics = adapter.verify(repo, 10.minutes)
        assertTrue(diagnostics.isNotEmpty(), "Guava 21.0 breaks the build")
        assertFalse(localRepository.resolve("com/google/guava/guava/18.0").exists(), "the runner has never seen 18.0")
        val verifyRuns = commands.size

        val text = adapter.apiChanges(repo, change, diagnostics)

        assertContains(
            text,
            "Moved members:\n- com.google.common.base.Objects.firstNonNull(java.lang.Object, java.lang.Object) → " +
                "com.google.common.base.MoreObjects.firstNonNull(java.lang.Object, java.lang.Object)",
        )
        val fetches = commands.drop(verifyRuns)
        assertEquals(1, fetches.size, "$fetches")
        assertTrue(fetches.single().any { "maven-dependency-plugin" in it } && "-N" in fetches.single(), "${fetches.single()}")
        assertTrue(localRepository.resolve("com/google/guava/guava/18.0/guava-18.0.jar").exists())
        assertEquals("", fixture.git("status", "--porcelain"), "the fetch writes nothing into the repository")
        assertEquals(text, adapter.apiChanges(repo, change, diagnostics))
        assertEquals(1, commands.size - verifyRuns, "the next attempt fetches nothing")
    }
}
