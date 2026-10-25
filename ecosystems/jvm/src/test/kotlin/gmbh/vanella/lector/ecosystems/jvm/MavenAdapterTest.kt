package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.LockfileUpdate
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.Recipe
import gmbh.vanella.lector.core.RecipeRun
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.ToolError
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class MavenAdapterTest {
    @TempDir
    lateinit var repo: Path

    private val root by lazy { repo.toRealPath() }
    private val runs = mutableListOf<List<String>>()

    @Test
    fun `no lockfile follows a build-file edit, Maven has none`() {
        assertEquals(LockfileUpdate.Updated(emptyList()), adapter().updateLockfiles(repo, 1.minutes))
        assertEquals(
            emptyList(),
            adapter().loosening("pom.xml", "<skipTests>false</skipTests>\n", ""),
            "removing a setting loosens nothing",
        )
        assertEquals(emptyList(), runs)
    }

    @Test
    fun `speaks for the maven ecosystem, with Maven's version order`() {
        val adapter = MavenAdapter()

        assertEquals("maven", adapter.ecosystem)
        assertEquals(MavenVersionScheme, adapter.versionScheme)
    }

    @Test
    fun `runs a recipe with the pinned rewrite plugin's runNoFork goal, by coordinates`() {
        val settings = repo.resolve("settings.xml")
        val adapter = adapter(settings = settings)

        val run = adapter.recipeRunner.run(repo, Recipe("org.example.UpgradeBoot", "org.example:recipes:1.2.3", "Apache License 2.0"))

        assertEquals(RecipeRun.Applied, run)
        assertEquals(
            listOf(
                listOf(
                    "mvn",
                    "-B",
                    "-fae",
                    "-Dgpg.skip=true",
                    "--settings",
                    settings.toAbsolutePath().toString(),
                    "org.openrewrite.maven:rewrite-maven-plugin:9.9.9:runNoFork",
                    "-Drewrite.recipeArtifactCoordinates=org.example:recipes:1.2.3",
                    "-Drewrite.activeRecipes=org.example.UpgradeBoot",
                ),
            ),
            runs,
        )
    }

    @Test
    fun `never runs the plain run goal, which would build the project first`() {
        adapter().recipeRunner.run(repo, Recipe("org.example.UpgradeBoot", "org.example:recipes:1.2.3", "Apache License 2.0"))

        val goals = runs.single().filter { it.startsWith("org.openrewrite") }
        assertEquals(listOf("org.openrewrite.maven:rewrite-maven-plugin:9.9.9:runNoFork"), goals)
        assertFalse(runs.single().any { it == "run" || it.endsWith(":run") })
    }

    @Test
    fun `a recipe run that fails reports the end of Maven's output`() {
        val adapter =
            MavenAdapter(plugins = PLUGINS) { _, _, _, _ -> ProcessResult(1, "[ERROR] Recipe not found: org.example.UpgradeBoot\n") }

        val run = adapter.recipeRunner.run(repo, Recipe("org.example.UpgradeBoot", "org.example:recipes:1.2.3", "Apache License 2.0"))

        assertEquals(
            RecipeRun.Failed("Maven failed with exit code 1. The end of its output:\n[ERROR] Recipe not found: org.example.UpgradeBoot"),
            run,
        )
    }

    @Test
    fun `reads resolved versions from the effective POM, written to a file outside the repository`() {
        var output: Path? = null
        val adapter =
            MavenAdapter(plugins = PLUGINS) { command, _, _, _ ->
                runs += command
                val file = Path.of(command.single { it.startsWith("-Doutput=") }.removePrefix("-Doutput="))
                file.writeText(effectivePom("org.example", "lib", "1.2.3"))
                output = file
                ProcessResult(0, "")
            }

        val versions = adapter.resolvedVersions.read(repo)

        assertEquals(mapOf("org.example:lib" to "1.2.3"), versions)
        assertEquals(
            listOf("mvn", "-B", "-fae", "-Dgpg.skip=true", "org.apache.maven.plugins:maven-help-plugin:8.8.8:effective-pom"),
            runs.single().dropLast(1),
        )
        val file = assertNotNull(output)
        assertFalse(file.startsWith(root), "$file is outside $root")
        assertFalse(file.exists(), "$file is removed")
    }

    @Test
    fun `every Maven run gets the JDK the project needs as JAVA_HOME, and recipe runs too`() {
        repo.resolve("pom.xml").writeText("<project><properties><java.version>17</java.version></properties></project>")
        val environments = mutableListOf<Map<String, String>>()
        val jdks = mapOf(17 to Path.of("/opt/java/17"), 21 to Path.of("/opt/java/21"))
        val adapter =
            MavenAdapter(plugins = PLUGINS, jdks = jdks) { _, _, environment, _ ->
                environments += environment
                ProcessResult(0, "")
            }

        adapter.verify(repo, 1.hours)
        adapter.recipeRunner.run(repo, Recipe("org.example.UpgradeBoot", "org.example:recipes:1.0", "Apache-2.0"))

        assertEquals(3, environments.size)
        assertEquals(listOf(mapOf("JAVA_HOME" to "/opt/java/17")), environments.distinct())
    }

    @Test
    fun `builds and test runs get the given timeout, never more than an hour, and the other runs get an hour`() {
        repo.resolve("pom.xml").writeText("<project/>")
        val timeouts = mutableListOf<Pair<String, Duration>>()
        val adapter =
            MavenAdapter(plugins = PLUGINS) { command, _, _, timeout ->
                timeouts += command[4] to timeout
                ProcessResult(0, "")
            }
        val test = Diagnostic(null, null, null, null, "failed", Severity.ERROR, "surefire", "com.example.AppTest#runs")

        adapter.verify(repo, 10.minutes)
        adapter.runTests(repo, listOf(test), 3.hours)
        adapter.recipeRunner.run(repo, Recipe("org.example.UpgradeBoot", "org.example:recipes:1.0", "Apache-2.0"))

        assertEquals(
            listOf("clean", "verify", "clean", "org.openrewrite.maven:rewrite-maven-plugin:9.9.9:runNoFork"),
            timeouts.map { it.first },
        )
        assertEquals(10.minutes, timeouts[0].second)
        assertTrue(timeouts[1].second in 9.minutes..10.minutes, "verify gets what test-compile left: ${timeouts[1].second}")
        assertEquals(listOf(1.hours, 1.hours), timeouts.drop(2).map { it.second })
    }

    @Test
    fun `without installed JDKs, Maven runs with the environment's JAVA_HOME`() {
        repo.resolve("pom.xml").writeText("<project/>")
        val environments = mutableListOf<Map<String, String>>()
        val adapter =
            MavenAdapter(plugins = PLUGINS) { _, _, environment, _ ->
                environments += environment
                ProcessResult(0, "")
            }

        adapter.verify(repo, 1.hours)

        assertEquals(listOf(emptyMap()), environments.distinct())
    }

    @Test
    fun `resolved versions that Maven can't read are a tool error with the end of its output`() {
        val adapter = MavenAdapter(plugins = PLUGINS) { _, _, _, _ -> ProcessResult(1, "[ERROR] Non-resolvable parent POM") }

        val error = assertFailsWith<ToolError> { adapter.resolvedVersions.read(repo) }

        assertEquals("Maven failed with exit code 1. The end of its output:\n[ERROR] Non-resolvable parent POM", error.message)
    }

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
        val diagnostics = adapter().verify(repo, 1.hours)

        assertEquals(emptyList(), diagnostics)
        assertEquals(
            listOf(
                listOf("mvn", "-B", "-fae", "-Dgpg.skip=true", "clean", "test-compile"),
                listOf("mvn", "-B", "-fae", "-Dgpg.skip=true", "verify"),
            ),
            runs,
        )
    }

    @Test
    fun `uses the Maven wrapper when the repository has one`() {
        repo.resolve("mvnw").createFile()

        adapter().verify(repo, 1.hours)

        assertEquals(listOf("$root/mvnw", "$root/mvnw"), runs.map { it.first() })
    }

    @Test
    fun `runs the wrapper as a process in the repository`() {
        repo.resolve("mvnw").writeText("#!/bin/sh\necho \"[ERROR] Failed to execute goal on project app: ran $*\"\nexit 1\n")
        repo.resolve("mvnw").toFile().setExecutable(true)

        val diagnostics = MavenAdapter().verify(repo, 1.hours)

        assertEquals(
            listOf("Failed to execute goal on project app: ran -B -fae -Dgpg.skip=true clean test-compile"),
            diagnostics.map {
                it.message
            },
        )
    }

    @Test
    fun `passes the Maven settings file to every run`() {
        val settings = repo.resolve("settings.xml")

        adapter(settings = settings).verify(repo, 1.hours)
        adapter(settings = settings).runTests(repo, listOf(testFailure("com.example.FooTest#bar")), 1.hours)

        assertEquals(3, runs.size)
        runs.forEach { assertContains(it.joinToString(" "), "-B -fae -Dgpg.skip=true --settings ${settings.toAbsolutePath()} ") }
    }

    @Test
    fun `the tests run are counted from the reports the verify left`() {
        repo
            .resolve("lib/target/surefire-reports/TEST-com.example.FooTest.xml")
            .createParentDirectories()
            .writeText("<testsuite><testcase name=\"a\"/><testcase name=\"b\"/></testsuite>")

        assertEquals(2, MavenAdapter(plugins = PLUGINS) { _, _, _, _ -> ProcessResult(0, "") }.testsRun(repo))
    }

    @Test
    fun `the tests run are unknown after a verify that skipped a module, until a verify skips none`() {
        repo
            .resolve("a/target/surefire-reports/TEST-a.ATest.xml")
            .createParentDirectories()
            .writeText("<testsuite><testcase name=\"one\"/></testsuite>")
        // Real Maven 3.9 output: `a` failed an enforcer rule in `verify`, so `b`, which depends on it, never ran its tests.
        val skipped =
            """
            [INFO] Reactor Summary for parent 1:
            [INFO]
            [INFO] parent ............................................. SUCCESS [  0.000 s]
            [INFO] a .................................................. FAILURE [  0.599 s]
            [INFO] b .................................................. SKIPPED
            [INFO] ------------------------------------------------------------------------
            [INFO] BUILD FAILURE
            """.trimIndent()
        var verifyOutput = skipped
        val adapter =
            MavenAdapter(plugins = PLUGINS) { command, _, _, _ ->
                ProcessResult(
                    0,
                    if ("verify" in
                        command
                    ) {
                        verifyOutput
                    } else {
                        ""
                    },
                )
            }

        adapter.verify(repo, 1.hours)
        val afterSkip = adapter.testsRun(repo)
        verifyOutput = skipped.replace("SKIPPED", "SUCCESS [  0.412 s]")
        adapter.verify(repo, 1.hours)

        assertEquals(null, afterSkip, "a build error before b's tests would hide what the update did to them")
        assertEquals(1, adapter.testsRun(repo))
    }

    @Test
    fun `doesn't run the tests when the code doesn't compile`() {
        val diagnostics =
            adapter(
                compileExitCode = 1,
                compileOutput = "[ERROR] $root/src/main/java/App.java:[3,5] cannot find symbol",
            ).verify(repo, 1.hours)

        assertEquals(listOf(Diagnostic("src/main/java/App.java", 3, 5, null, "cannot find symbol", Severity.ERROR, "javac")), diagnostics)
        assertEquals(1, runs.size)
    }

    @Test
    fun `doesn't run the tests when the compile stage fails without an error it understands`() {
        val diagnostics = adapter(compileExitCode = 1, compileOutput = "Error: could not download Maven").verify(repo, 1.hours)

        val failure = diagnostics.single()
        assertEquals(null, failure.file)
        assertEquals("maven", failure.tool)
        assertEquals("Maven failed with exit code 1. The end of its output:\nError: could not download Maven", failure.message)
        assertEquals(1, runs.size)
    }

    @Test
    fun `reports test failures from the test reports of the verify stage`() {
        val adapter = adapter(onVerify = { writeFailingReport("com.example.FooTest", "bar") })

        val diagnostics = adapter.verify(repo, 1.hours)

        assertEquals(listOf("com.example.FooTest#bar"), diagnostics.map { it.test })
    }

    @Test
    fun `the verify command never skips, filters, or ignores tests`() {
        adapter().verify(repo, 1.hours)

        val forbidden = listOf("skipTests", "maven.test.skip", "testFailureIgnore", "-Dtest=", "-Dit.test=", "skipITs")
        runs.flatten().forEach { argument -> forbidden.forEach { assertFalse(argument.contains(it), argument) } }
    }

    @Test
    fun `runs only the classes of the given unit tests, up to the test phase`() {
        val tests = listOf(testFailure("com.example.FooTest#a"), testFailure("com.example.FooTest#b"), testFailure("com.example.BarTest#c"))

        adapter().runTests(repo, tests, 1.hours)

        assertEquals(
            listOf(
                listOf(
                    "mvn",
                    "-B",
                    "-fae",
                    "-Dgpg.skip=true",
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

        adapter().runTests(repo, tests, 1.hours)

        assertEquals(
            listOf(
                listOf(
                    "mvn",
                    "-B",
                    "-fae",
                    "-Dgpg.skip=true",
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
    fun `runs no unit tests when only integration tests are given`() {
        adapter().runTests(repo, listOf(testFailure("com.example.AppIT#b", tool = "failsafe")), 1.hours)

        assertEquals(
            listOf(
                listOf(
                    "mvn",
                    "-B",
                    "-fae",
                    "-Dgpg.skip=true",
                    "clean",
                    "verify",
                    "-Dtest=lector.NoUnitTests",
                    "-Dsurefire.failIfNoSpecifiedTests=false",
                    "-Dit.test=com.example.AppIT",
                    "-Dit.failIfNoSpecifiedTests=false",
                ),
            ),
            runs,
        )
    }

    @Test
    fun `runTests returns the failures of its run`() {
        val adapter = adapter(onVerify = { writeFailingReport("com.example.FooTest", "a") })

        val diagnostics = adapter.runTests(repo, listOf(testFailure("com.example.FooTest#a")), 1.hours)

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

    private fun effectivePom(
        groupId: String,
        artifactId: String,
        version: String,
    ) = """<project><dependencies><dependency><groupId>$groupId</groupId><artifactId>$artifactId</artifactId>""" +
        """<version>$version</version></dependency></dependencies></project>"""

    /** A Maven adapter whose runs are recorded and answered with canned results instead of running Maven. */
    private fun adapter(
        settings: Path? = null,
        compileExitCode: Int = 0,
        compileOutput: String = "",
        onVerify: () -> Unit = {},
    ) = MavenAdapter(settings, PLUGINS) { command, workDir, _, _ ->
        assertEquals(root, workDir)
        runs += command
        if ("test-compile" in command) {
            ProcessResult(compileExitCode, compileOutput)
        } else {
            onVerify()
            ProcessResult(0, "")
        }
    }

    private companion object {
        val PLUGINS =
            MavenPlugins(
                "org.openrewrite.maven:rewrite-maven-plugin:9.9.9",
                "org.apache.maven.plugins:maven-help-plugin:8.8.8",
                "org.apache.maven.plugins:maven-dependency-plugin:7.7.7",
            )
    }
}
