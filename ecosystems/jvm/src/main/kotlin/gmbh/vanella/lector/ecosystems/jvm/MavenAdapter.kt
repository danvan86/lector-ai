package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.LanguageSupport
import gmbh.vanella.lector.core.LockfileUpdate
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.RecipeRun
import gmbh.vanella.lector.core.RecipeRunner
import gmbh.vanella.lector.core.ResolvedVersions
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.runProcess
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

private val MAVEN_TIMEOUT = 1.hours
private const val OUTPUT_TAIL_LINES = 20

// A surefire filter that matches no test class.
private const val NO_UNIT_TESTS = "lector.NoUnitTests"

/**
 * Builds and tests Maven projects. Runs the project's wrapper (`./mvnw`) if it has one, otherwise `mvn` from the
 * environment, with the environment's Maven settings, or [settings] if given. The JDK is the smallest of the installed
 * [jdks] (release → `JAVA_HOME`) that the project's release needs ([JdkSelection]); without any, the one `JAVA_HOME`
 * points to.
 *
 * Maven runs in two stages, both in batch mode with fail-at-end, so every module reports its problems: first
 * `test-compile`, and only if that is clean, `verify`.
 *
 * Grounding reads the JARs in [localRepository]. A `localRepository` set in the Maven settings is not followed.
 */
class MavenAdapter(
    private val settings: Path? = null,
    plugins: MavenPlugins = MavenPlugins.load(),
    localRepository: Path = Path.of(System.getProperty("user.home"), ".m2", "repository"),
    jdks: Map<Int, Path> = emptyMap(),
    private val run: (
        command: List<String>,
        workDir: Path,
        environment: Map<String, String>,
        timeout: Duration,
    ) -> ProcessResult = { command, workDir, environment, timeout ->
        runProcess(command, workDir, timeout, environment)
    },
) : EcosystemAdapter {
    private val grounding = JavapGrounding(localRepositoryJars(localRepository))
    private val jdkSelection = JdkSelection(jdks)

    override val ecosystem = "maven"
    override val versionScheme = MavenVersionScheme

    /**
     * Runs a recipe with the rewrite plugin's `runNoFork` goal, by coordinates: no change to `pom.xml`, and no forked
     * lifecycle, which would compile the project first and fail exactly when the update broke it. The user's Maven
     * fetches the recipe artifact, through the configured mirror.
     */
    override val recipeRunner =
        RecipeRunner { repo, recipe ->
            val root = repo.toRealPath()
            val goals =
                listOf(
                    "${plugins.rewrite}:runNoFork",
                    "-Drewrite.recipeArtifactCoordinates=${recipe.artifact}",
                    "-Drewrite.activeRecipes=${recipe.name}",
                )
            val result = maven(root, goals)
            if (result.exitCode == 0) RecipeRun.Applied else RecipeRun.Failed(failure(result))
        }

    /** Reads the versions from Maven's effective POM. It resolves parents and imported BOMs without compiling anything. */
    override val resolvedVersions =
        ResolvedVersions { repo ->
            val root = repo.toRealPath()
            val output = Files.createTempFile("lector-effective-pom", ".xml")
            try {
                val result = maven(root, listOf("${plugins.help}:effective-pom", "-Doutput=$output"))
                if (result.exitCode != 0) throw ToolError(failure(result))
                EffectivePom.versions(output.readText())
            } finally {
                output.deleteIfExists()
            }
        }

    override val languageSupport: LanguageSupport = JvmLanguageSupport

    override fun detect(repo: Path) = repo.resolve("pom.xml").isRegularFile()

    override fun verify(
        repo: Path,
        timeout: Duration,
    ): List<Diagnostic> {
        val root = repo.toRealPath()
        val started = TimeSource.Monotonic.markNow()
        return problems(root, listOf("clean", "test-compile"), timeout).ifEmpty {
            problems(root, listOf("verify"), timeout - started.elapsedNow())
        }
    }

    /** The test cases in the surefire and failsafe reports that the last verify left. */
    override fun testsRun(repo: Path) = MAVEN_REPORTS.count(repo.toRealPath())

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
        timeout: Duration,
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
        return problems(repo.toRealPath(), goals, timeout)
    }

    override fun ground(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ) = grounding.ground(repo, change, diagnostics)

    override fun clusterKey(diagnostic: Diagnostic) = ClusterKeys.of(diagnostic)

    /**
     * The POMs, and what Maven reads at the root besides them: the wrapper and `.mvn/`. Both can set properties, so the
     * blocklist and the downgrade check must see them.
     */
    override fun isBuildFile(file: String) =
        file == "pom.xml" || file.endsWith("/pom.xml") || file == "mvnw" || file == "mvnw.cmd" || file.startsWith(".mvn/")

    override fun isEditable(file: String) = JvmScope.isEditable(file)

    /** Maven has no lockfile: the POMs name every version. */
    override fun updateLockfiles(
        repo: Path,
        timeout: Duration,
    ) = LockfileUpdate.Updated(emptyList())

    override fun testDisablingConfig(
        file: String,
        content: String,
        line: Int,
    ) = MavenTestSkipping.reason(content, line)

    /** Runs Maven and collects the problems from its output and the test reports. */
    private fun problems(
        root: Path,
        goals: List<String>,
        timeout: Duration,
    ): List<Diagnostic> {
        val result = maven(root, goals, timeout)
        val problems = MavenConsole.parse(result.output, root) + MAVEN_REPORTS.parse(root)
        if (result.exitCode != 0 && problems.isEmpty()) return listOf(unexplainedFailure(result))
        return problems
    }

    /** Runs Maven with [goals] in [root], on the JDK the project needs, for at most [timeout] and never more than an hour. */
    private fun maven(
        root: Path,
        goals: List<String>,
        timeout: Duration = MAVEN_TIMEOUT,
    ): ProcessResult {
        val javaHome = jdkSelection.javaHome(root)
        val environment = if (javaHome == null) emptyMap() else mapOf("JAVA_HOME" to javaHome.toString())
        return run(command(root) + goals, root, environment, minOf(timeout, MAVEN_TIMEOUT))
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
    private fun unexplainedFailure(result: ProcessResult) = Diagnostic(null, null, null, null, failure(result), Severity.ERROR, "maven")

    private fun failure(result: ProcessResult): String {
        val tail =
            result.output
                .lines()
                .filter { it.isNotBlank() }
                .takeLast(OUTPUT_TAIL_LINES)
        return (listOf("Maven failed with exit code ${result.exitCode}. The end of its output:") + tail).joinToString("\n")
    }
}
