package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.Severity
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MavenAdapterTest {
    @TempDir
    lateinit var repo: Path

    private val root by lazy { repo.toRealPath() }
    private val runs = mutableListOf<List<String>>()

    @Test
    fun `detects a repository with a pom_xml at the root`() {
        repo.resolve("pom.xml").createFile()

        assertTrue(MavenAdapter().detect(repo))
    }

    @Test
    fun `doesn't detect a repository without a pom_xml at the root`() {
        repo.resolve("module/pom.xml").createParentDirectories().createFile()
        repo.resolve("build.gradle.kts").createFile()

        assertFalse(MavenAdapter().detect(repo))
    }

    @Test
    fun `compiles first, then verifies, both in batch mode with fail-at-end`() {
        val diagnostics = adapter().verify(repo)

        assertEquals(emptyList(), diagnostics)
        assertEquals(
            listOf(
                listOf("mvn", "-B", "-fae", "clean", "test-compile"),
                listOf("mvn", "-B", "-fae", "verify"),
            ),
            runs,
        )
    }

    @Test
    fun `uses the Maven wrapper when the repository has one`() {
        repo.resolve("mvnw").createFile()

        adapter().verify(repo)

        assertEquals(listOf("$root/mvnw", "$root/mvnw"), runs.map { it.first() })
    }

    @Test
    fun `runs the wrapper as a process in the repository`() {
        repo.resolve("mvnw").writeText("#!/bin/sh\necho \"[ERROR] Failed to execute goal on project app: ran $*\"\nexit 1\n")
        repo.resolve("mvnw").toFile().setExecutable(true)

        val diagnostics = MavenAdapter().verify(repo)

        assertEquals(listOf("Failed to execute goal on project app: ran -B -fae clean test-compile"), diagnostics.map { it.message })
    }

    @Test
    fun `passes the Maven settings file to every run`() {
        val settings = repo.resolve("settings.xml")

        adapter(settings = settings).verify(repo)
        adapter(settings = settings).runTests(repo, listOf(testFailure("com.example.FooTest#bar")))

        assertEquals(3, runs.size)
        runs.forEach { assertContains(it.joinToString(" "), "-B -fae --settings ${settings.toAbsolutePath()} ") }
    }

    @Test
    fun `doesn't run the tests when the code doesn't compile`() {
        val diagnostics =
            adapter(
                compileExitCode = 1,
                compileOutput = "[ERROR] $root/src/main/java/App.java:[3,5] cannot find symbol",
            ).verify(repo)

        assertEquals(listOf(Diagnostic("src/main/java/App.java", 3, 5, null, "cannot find symbol", Severity.ERROR, "javac")), diagnostics)
        assertEquals(1, runs.size)
    }

    @Test
    fun `doesn't run the tests when the compile stage fails without an error it understands`() {
        val diagnostics = adapter(compileExitCode = 1, compileOutput = "Error: could not download Maven").verify(repo)

        val failure = diagnostics.single()
        assertEquals(null, failure.file)
        assertEquals("maven", failure.tool)
        assertEquals("Maven failed with exit code 1. The end of its output:\nError: could not download Maven", failure.message)
        assertEquals(1, runs.size)
    }

    @Test
    fun `reports test failures from the test reports of the verify stage`() {
        val adapter = adapter(onVerify = { writeFailingReport("com.example.FooTest", "bar") })

        val diagnostics = adapter.verify(repo)

        assertEquals(listOf("com.example.FooTest#bar"), diagnostics.map { it.test })
    }

    @Test
    fun `the verify command never skips, filters, or ignores tests`() {
        adapter().verify(repo)

        val forbidden = listOf("skipTests", "maven.test.skip", "testFailureIgnore", "-Dtest=", "-Dit.test=", "skipITs")
        runs.flatten().forEach { argument -> forbidden.forEach { assertFalse(argument.contains(it), argument) } }
    }

    @Test
    fun `runs only the classes of the given unit tests, up to the test phase`() {
        val tests = listOf(testFailure("com.example.FooTest#a"), testFailure("com.example.FooTest#b"), testFailure("com.example.BarTest#c"))

        adapter().runTests(repo, tests)

        assertEquals(
            listOf(
                listOf(
                    "mvn",
                    "-B",
                    "-fae",
                    "clean",
                    "test",
                    "-Dtest=com.example.FooTest,com.example.BarTest",
                    "-Dsurefire.failIfNoSpecifiedTests=false",
                ),
            ),
            runs,
        )
    }

    @Test
    fun `runs the classes of integration tests with failsafe, up to the verify phase`() {
        val tests = listOf(testFailure("com.example.FooTest#a"), testFailure("com.example.AppIT#b", tool = "failsafe"))

        adapter().runTests(repo, tests)

        assertEquals(
            listOf(
                listOf(
                    "mvn",
                    "-B",
                    "-fae",
                    "clean",
                    "verify",
                    "-Dtest=com.example.FooTest",
                    "-Dsurefire.failIfNoSpecifiedTests=false",
                    "-Dit.test=com.example.AppIT",
                    "-Dit.failIfNoSpecifiedTests=false",
                ),
            ),
            runs,
        )
    }

    @Test
    fun `runs only integration tests when only integration tests are given`() {
        adapter().runTests(repo, listOf(testFailure("com.example.AppIT#b", tool = "failsafe")))

        assertEquals(
            listOf(listOf("mvn", "-B", "-fae", "clean", "verify", "-Dit.test=com.example.AppIT", "-Dit.failIfNoSpecifiedTests=false")),
            runs,
        )
    }

    @Test
    fun `runTests returns the failures of its run`() {
        val adapter = adapter(onVerify = { writeFailingReport("com.example.FooTest", "a") })

        val diagnostics = adapter.runTests(repo, listOf(testFailure("com.example.FooTest#a")))

        assertEquals(listOf("com.example.FooTest#a"), diagnostics.map { it.test })
    }

    private fun testFailure(
        test: String,
        tool: String = "surefire",
    ) = Diagnostic(null, null, null, null, "failed", Severity.ERROR, tool, test)

    private fun writeFailingReport(
        className: String,
        method: String,
    ) {
        repo.resolve("target/surefire-reports").createDirectories().resolve("TEST-$className.xml").writeText(
            """<testsuite><testcase name="$method" classname="$className"><failure message="boom"/></testcase></testsuite>""",
        )
    }

    /** A Maven adapter whose runs are recorded and answered with canned results instead of running Maven. */
    private fun adapter(
        settings: Path? = null,
        compileExitCode: Int = 0,
        compileOutput: String = "",
        onVerify: () -> Unit = {},
    ) = MavenAdapter(settings) { command, workDir ->
        assertEquals(root, workDir)
        runs += command
        if ("test-compile" in command) {
            ProcessResult(compileExitCode, compileOutput)
        } else {
            onVerify()
            ProcessResult(0, "")
        }
    }
}
