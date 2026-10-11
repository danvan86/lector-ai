package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import gmbh.vanella.lector.core.BaseCommit
import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.LanguageSupport
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.Verification
import gmbh.vanella.lector.ecosystems.jvm.MavenVersionScheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerifyCommandTest {
    @TempDir
    lateinit var repo: Path

    private val compileError = Diagnostic("src/App.java", 3, 5, null, "cannot find symbol", Severity.ERROR, "javac")
    private val oldBug = Diagnostic("src/OldTest.java", 9, null, null, "expected <1>", Severity.ERROR, "surefire", "OldTest#fails")
    private val flaky = Diagnostic(null, null, null, null, "timed out", Severity.ERROR, "surefire", "SlowTest#waits")

    private val settingsPassed = mutableListOf<Path?>()
    private val basesResolved = mutableListOf<String>()

    @Test
    fun `a clean repository exits with 0 and prints the text report`() {
        val result = command(FakeAdapter()).test(repo.toString())

        assertEquals(TextReport.render(Verification()) + "\n", result.stdout)
        assertEquals("", result.stderr)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `a problem caused by the update exits with 1`() {
        val result = command(FakeAdapter(verify = listOf(compileError))).test(repo.toString())

        assertContains(result.stdout, "Caused by the update (1)\n  - [javac] src/App.java:3:5: cannot find symbol")
        assertEquals("", result.stderr)
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `pre-existing and flaky failures exit with 0`() {
        val adapter = FakeAdapter(verify = listOf(oldBug, flaky), headRerun = listOf(oldBug), baseRun = listOf(oldBug))

        val result = command(adapter).test("$repo --base main")

        assertContains(result.stdout, "Pre-existing (1)")
        assertContains(result.stdout, "Flaky (1)")
        assertEquals(listOf("main"), basesResolved)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `without --base a failing test is unattributed and exits with 0`() {
        val result = command(FakeAdapter(verify = listOf(oldBug), headRerun = listOf(oldBug))).test(repo.toString())

        assertContains(result.stdout, "Unattributed (1)\n  - [surefire] OldTest#fails")
        assertEquals(emptyList(), basesResolved)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `--format json prints only the verification as JSON`() {
        val result = command(FakeAdapter(verify = listOf(compileError))).test("$repo --format json")

        val json = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(listOf("causedByUpdate", "preExisting", "flaky", "unattributed"), json.keys.toList())
        assertEquals(1, json.getValue("causedByUpdate").jsonArray.size)
        assertEquals("", result.stderr)
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `--maven-settings is handed to the adapters`() {
        val settings = repo.resolve("settings.xml").createFile()

        command(FakeAdapter()).test("$repo --maven-settings $settings")

        assertEquals(listOf<Path?>(settings), settingsPassed)
    }

    @Test
    fun `a repository no adapter supports is a tool error with exit 2`() {
        val result = command(FakeAdapter(detects = false)).test(repo.toString())

        assertEquals("", result.stdout)
        assertEquals("Error: No supported ecosystem in $repo. Supported: Maven (pom.xml at the root).\n", result.stderr)
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `a repository that doesn't exist is a tool error with exit 2`() {
        val result = command(FakeAdapter()).test(repo.resolve("missing").toString())

        assertEquals("", result.stdout)
        assertEquals("Error: ${repo.resolve("missing")} is not a directory\n", result.stderr)
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `a Maven settings file that doesn't exist is a tool error with exit 2`() {
        val result = command(FakeAdapter()).test("$repo --maven-settings ${repo.resolve("missing.xml")}")

        assertEquals("", result.stdout)
        assertEquals("Error: Maven settings ${repo.resolve("missing.xml")} is not a file\n", result.stderr)
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `an invalid --base is a tool error with exit 2, before anything is built`() {
        val adapter = FakeAdapter()

        val result = VerifyCommand(adapters = { listOf(adapter) }).test("$repo --base no-such-ref")

        assertEquals("", result.stdout)
        assertContains(result.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertEquals(2, result.statusCode)
        assertEquals(0, adapter.verifyCalls)
    }

    @Test
    fun `a tool error while verifying exits with 2`() {
        val adapter = FakeAdapter(failure = ToolError("Cannot run mvn: No such file or directory"))

        val result = command(adapter).test(repo.toString())

        assertEquals("", result.stdout)
        assertEquals("Error: Cannot run mvn: No such file or directory\n", result.stderr)
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `an unexpected failure is a tool error with exit 2, not a problem caused by the update`() {
        val adapter = FakeAdapter(failure = IllegalStateException("report parser crashed"))

        val result = command(adapter).test(repo.toString())

        assertEquals("", result.stdout)
        assertEquals("Error: Unexpected failure: java.lang.IllegalStateException: report parser crashed\n", result.stderr)
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `an unknown format is a usage error with exit 2`() {
        val run = lector(FakeAdapter()).executeInProcess("verify", repo.toString(), "--format", "xml")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "Usage: lector verify")
        assertContains(run.stderr, "invalid value for --format")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `a missing repository argument is a usage error with exit 2`() {
        val run = lector(FakeAdapter()).executeInProcess("verify")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "missing argument <repo>")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `run through the root command, a problem caused by the update still exits with 1`() {
        val run = lector(FakeAdapter(verify = listOf(compileError))).executeInProcess("verify", repo.toString())

        assertContains(run.stdout, "Caused by the update (1)")
        assertEquals("", run.stderr)
        assertEquals(1, run.exitCode)
    }

    private fun lector(adapter: FakeAdapter) = LectorCommand("1.2.3").subcommands(command(adapter))

    private fun command(adapter: FakeAdapter) =
        VerifyCommand(
            adapters = { settings ->
                settingsPassed += settings
                listOf(adapter)
            },
            baseCommit = { _, ref ->
                basesResolved += ref
                object : BaseCommit {
                    override fun <T> checkout(block: (Path) -> T) = block(repo.resolve("base"))
                }
            },
        )

    private inner class FakeAdapter(
        private val detects: Boolean = true,
        private val verify: List<Diagnostic> = emptyList(),
        private val headRerun: List<Diagnostic> = emptyList(),
        private val baseRun: List<Diagnostic> = emptyList(),
        private val failure: RuntimeException? = null,
    ) : EcosystemAdapter {
        var verifyCalls = 0

        override val ecosystem = "maven"

        override val languageSupport = LanguageSupport { _, _ -> null }
        override val versionScheme = MavenVersionScheme
        override val recipeRunner = null
        override val resolvedVersions = null

        override fun detect(repo: Path) = detects

        override fun verify(repo: Path): List<Diagnostic> {
            verifyCalls++
            failure?.let { throw it }
            return verify
        }

        override fun runTests(
            repo: Path,
            tests: List<Diagnostic>,
        ) = if (repo == this@VerifyCommandTest.repo) headRerun else baseRun

        override fun ground(
            repo: Path,
            change: DependencyChange,
            diagnostics: List<Diagnostic>,
        ) = ""

        override fun clusterKey(diagnostic: Diagnostic) = null

        override fun isBuildFile(file: String) = false
    }
}
