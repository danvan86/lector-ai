package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.runProcess
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.time.Duration.Companion.hours

private val MAVEN_TIMEOUT = 1.hours
private const val OUTPUT_TAIL_LINES = 20

// A surefire filter that matches no test class.
private const val NO_UNIT_TESTS = "lector.NoUnitTests"

/**
 * Builds and tests Maven projects. Runs the project's wrapper (`./mvnw`) if it has one, otherwise `mvn` from the
 * environment, with the JDK that `JAVA_HOME` points to and the environment's Maven settings, or [settings] if given.
 *
 * Maven runs in two stages, both in batch mode with fail-at-end, so every module reports its problems: first
 * `test-compile`, and only if that is clean, `verify`.
 */
class MavenAdapter(
    private val settings: Path? = null,
    private val run: (command: List<String>, workDir: Path) -> ProcessResult = { command, workDir ->
        runProcess(command, workDir, MAVEN_TIMEOUT)
    },
) : EcosystemAdapter {
    override val ecosystem = "maven"
    override val versionScheme = MavenVersionScheme
    override val recipeRunner = null
    override val resolvedVersions = null

    override fun detect(repo: Path) = repo.resolve("pom.xml").isRegularFile()

    override fun verify(repo: Path): List<Diagnostic> {
        val root = repo.toRealPath()
        return maven(root, listOf("clean", "test-compile")).ifEmpty { maven(root, listOf("verify")) }
    }

    /**
     * Reruns the classes of the given tests: unit tests with surefire (up to `test`), integration tests with failsafe
     * (up to `verify`). Whole classes, because surefire can't select every test method by name (parameterized tests, for
     * example).
     *
     * `verify` runs surefire too. Without a filter it would run every unit test, and a failing one would stop failsafe in
     * its module. So surefire always gets a filter, one that matches no class if no unit test is given.
     */
    override fun runTests(
        repo: Path,
        tests: List<Diagnostic>,
    ): List<Diagnostic> {
        val (integrationTests, unitTests) = tests.partition { it.tool == "failsafe" }
        val goals =
            buildList {
                add("clean")
                add(if (integrationTests.isEmpty()) "test" else "verify")
                add("-Dtest=${testClasses(unitTests).ifEmpty { NO_UNIT_TESTS }}")
                add("-Dsurefire.failIfNoSpecifiedTests=false")
                if (integrationTests.isNotEmpty()) {
                    add("-Dit.test=${testClasses(integrationTests)}")
                    add("-Dit.failIfNoSpecifiedTests=false")
                }
            }
        return maven(repo.toRealPath(), goals)
    }

    /** Runs Maven and collects the problems from its output and the test reports. */
    private fun maven(
        root: Path,
        goals: List<String>,
    ): List<Diagnostic> {
        val result = run(command(root) + goals, root)
        val problems = MavenConsole.parse(result.output, root) + TestReports.parse(root)
        if (result.exitCode != 0 && problems.isEmpty()) return listOf(unexplainedFailure(result))
        return problems
    }

    private fun command(root: Path): List<String> {
        val wrapper = root.resolve("mvnw")
        val executable = if (wrapper.isRegularFile()) wrapper.toString() else "mvn"
        val settingsOption = settings?.let { listOf("--settings", it.toAbsolutePath().toString()) }.orEmpty()
        return listOf(executable, "-B", "-fae") + settingsOption
    }

    private fun testClasses(tests: List<Diagnostic>) =
        tests
            .mapNotNull { it.test }
            .map { it.substringBefore('#') }
            .distinct()
            .joinToString(",")

    // A failed build must never look green, even when its output has no error this parser knows.
    private fun unexplainedFailure(result: ProcessResult): Diagnostic {
        val tail =
            result.output
                .lines()
                .filter { it.isNotBlank() }
                .takeLast(OUTPUT_TAIL_LINES)
        val message = (listOf("Maven failed with exit code ${result.exitCode}. The end of its output:") + tail).joinToString("\n")
        return Diagnostic(null, null, null, null, message, Severity.ERROR, "maven")
    }
}
