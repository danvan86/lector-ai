package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.testing.test
import gmbh.vanella.lector.ecosystems.jvm.GradleAdapter
import gmbh.vanella.lector.ecosystems.jvm.MavenAdapter
import gmbh.vanella.lector.ecosystems.typescript.TypeScriptAdapter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RootCommandTest {
    @Test
    fun `root command is lector with the given version`() {
        val result = rootCommand("1.2.3").test("--version")

        assertEquals("1.2.3\n", result.stdout)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `help lists the verify, migrate, heal, and pr commands`() {
        val result = rootCommand("1.2.3").test("--help")

        assertContains(result.stdout, "Commands:")
        assertContains(result.stdout, "verify")
        assertContains(result.stdout, "migrate")
        assertContains(result.stdout, "heal")
        assertContains(result.stdout, "pr ")
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `verify detects Maven repositories`(
        @TempDir repo: Path,
    ) {
        repo.resolve("pom.xml").createFile()

        // Detection passes, so verify gets as far as the base ref, which fails before Maven runs.
        val result = rootCommand("1.2.3").test("verify $repo --base no-such-ref")

        assertContains(result.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `migrate detects Maven repositories`(
        @TempDir repo: Path,
    ) {
        repo.resolve("pom.xml").createFile()

        val result = rootCommand("1.2.3").test("migrate $repo --package g:a --from 1.0 --to 2.0 --base no-such-ref")

        assertContains(result.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `heal detects Maven repositories`(
        @TempDir repo: Path,
    ) {
        repo.resolve("pom.xml").createFile()

        val result = rootCommand("1.2.3").test("heal $repo --package g:a --from 1.0 --to 2.0 --base no-such-ref")

        assertContains(result.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `pr reads the PR from the event with the GitHub adapter and checks it against the Renovate source`(
        @TempDir dir: Path,
    ) {
        val event = dir.resolve("event.json")
        val head = """{"ref": "feature/x", "sha": "1", "repo": {"full_name": "acme/app"}}"""
        val base = """{"sha": "2", "repo": {"full_name": "acme/app"}}"""
        event.writeText(
            """{"pull_request": {"number": 1, "state": "open", "merged": false, "labels": [], "body": null, "user": {"login": "renovate[bot]"}, "head": $head, "base": $base}}""",
        )

        val result =
            rootCommand("1.2.3").test(
                "pr $dir --event $event --bot-name b --bot-email b@example.com",
                envvars = mapOf("LECTOR_GITHUB_TOKEN" to "t"),
            )

        assertEquals("Skipped: no update source recognizes the PR by renovate[bot] on feature/x\n", result.stdout)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `verify detects Gradle repositories, and refuses one with a pom_xml next to its Gradle files`(
        @TempDir repo: Path,
    ) {
        repo.resolve("settings.gradle.kts").createFile()
        repo.resolve("gradlew").createFile()
        repo.resolve("gradle/wrapper").createDirectories().resolve("gradle-wrapper.properties").writeText(
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.1.0-bin.zip\n",
        )

        val detected = rootCommand("1.2.3").test("verify $repo --base no-such-ref")
        repo.resolve("pom.xml").createFile()
        val both = rootCommand("1.2.3").test("verify $repo")

        assertContains(detected.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertContains(both.stderr, "Error: $repo has both a pom.xml and Gradle build files at the root.")
        assertEquals(listOf(2, 2), listOf(detected.statusCode, both.statusCode))
    }

    @Test
    fun `Gradle comes before Maven, and gets the Gradle user home and the shipped OpenRewrite plugin from the environment`() {
        val environment =
            mapOf(
                "GRADLE_USER_HOME" to "/github/home/.gradle",
                "LECTOR_REWRITE_GRADLE_PLUGIN" to "/opt/lector/rewrite-gradle-plugin",
            )

        val adapters = adapters(BuildConfig(), environment)
        val withoutPlugin = adapters(BuildConfig(), mapOf("LECTOR_REWRITE_GRADLE_PLUGIN" to " "))

        assertEquals(listOf(GradleAdapter::class, MavenAdapter::class, TypeScriptAdapter::class), adapters.map { it::class })
        assertNotNull(adapters.first().recipeRunner)
        assertNull(withoutPlugin.first().recipeRunner, "a blank value is no plugin")
    }

    @Test
    fun `verify detects TypeScript repositories, and refuses one without a lockfile`(
        @TempDir repo: Path,
    ) {
        repo.resolve("package.json").writeText("{}")
        repo.resolve("tsconfig.json").writeText("{}")

        val result = rootCommand("1.2.3").test("verify $repo")

        assertEquals(
            "Error: $repo has no lockfile. lector-ai installs exactly what the lockfile says: commit a package-lock.json, " +
                "pnpm-lock.yaml, or yarn.lock.\n",
            result.stderr,
        )
        assertEquals(2, result.statusCode)
    }
}
