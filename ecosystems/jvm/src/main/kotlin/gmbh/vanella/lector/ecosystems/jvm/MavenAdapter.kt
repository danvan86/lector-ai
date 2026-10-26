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
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private val logger = LoggerFactory.getLogger(MavenAdapter::class.java)

private val MAVEN_TIMEOUT = 1.hours
private val FETCH_TIMEOUT = 2.minutes
private const val OUTPUT_TAIL_LINES = 20

// A surefire filter that matches no test class.
private const val NO_UNIT_TESTS = "lector.NoUnitTests"

// Signing is never what an update breaks, and it fails wherever `gpg` or its key is missing, like on a CI runner.
private const val SKIP_SIGNING = "-Dgpg.skip=true"

/**
 * Builds and tests Maven projects. Runs the project's wrapper (`./mvnw`) if it has one, otherwise `mvn` from the
 * environment, with the environment's Maven settings, or [settings] if given. The JDK is the smallest of the installed
 * [jdks] (release → `JAVA_HOME`) that the project's release needs ([JdkSelection]); without any, the one `JAVA_HOME`
 * points to.
 *
 * Maven runs in two stages, both in batch mode with fail-at-end, so every module reports its problems: first
 * `test-compile`, and only if that is clean, `verify`.
 *
 * Grounding reads the JARs in [localRepository]. A `localRepository` set in the Maven settings is not followed. For the
 * API changes, it may fetch the old version's JARs into it with the dependency plugin, through the project's Maven and
 * settings: only the artifacts the new version's grounding has, without their dependencies, once per run.
 */
class MavenAdapter(
    private val settings: Path? = null,
    private val plugins: MavenPlugins = MavenPlugins.load(),
    private val localRepository: Path = Path.of(System.getProperty("user.home"), ".m2", "repository"),
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
    private val apiChangesGrounding =
        ApiChangesGrounding(
            newJars = { _, change -> localRepositoryArtifacts(localRepository, change.group(), change.toVersion) },
            oldJars = ::oldJars,
        )

    // The old version's JARs of each group and version, by artifact ID: looked up once per run, failures included.
    private val oldJarsCache = mutableMapOf<Pair<String, String>, Map<String, List<Path>>>()

    // The checkouts whose last verify skipped a module after another failed: the skipped module's tests never ran.
    private val skippedModules = mutableSetOf<Path>()

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
        skippedModules.remove(root)
        return problems(root, listOf("clean", "test-compile"), timeout).ifEmpty {
            val result = maven(root, listOf("verify"), timeout - started.elapsedNow())
            if (MavenConsole.skippedModule(result.output)) skippedModules.add(root)
            problems(root, result)
        }
    }

    /**
     * The test cases in the surefire and failsafe reports that the last verify left. Unknown if it skipped a module: a
     * build error in one module would otherwise hide what the update did to the tests of the modules after it.
     */
    override fun testsRun(repo: Path): Int? {
        val root = repo.toRealPath()
        return if (root in skippedModules) null else MAVEN_REPORTS.count(root)
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

    override fun apiChanges(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ) = apiChangesGrounding.apiChanges(repo, change, diagnostics)

    override fun clusterKey(diagnostic: Diagnostic) = ClusterKeys.of(diagnostic)

    /**
     * The POMs, and what Maven reads at the root besides them: the wrapper and `.mvn/`. Both can set properties, so the
     * blocklist and the downgrade check must see them.
     */
    override fun isBuildFile(file: String) =
        file == "pom.xml" || file.endsWith("/pom.xml") || file == "mvnw" || file == "mvnw.cmd" || file.startsWith(".mvn/")

    override fun isEditable(file: String) = JvmScope.isEditable(file)

    /** Maven runs every test and check by default: removing a setting loosens nothing. */
    override fun loosening(
        file: String,
        before: String,
        after: String,
    ) = emptyList<String>()

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

    /**
     * The JARs of [artifacts] of [change]'s group at the old version, from the local repository, or fetched into it, the
     * change's own artifact first. An artifact the old version doesn't have is skipped; a fetch that times out or can't
     * run ends the lookup, as the next would likely fail the same way.
     */
    private fun oldJars(
        repo: Path,
        change: DependencyChange,
        artifacts: Set<String>,
    ) = oldJarsCache.getOrPut(change.group() to change.fromVersion) {
        val root = repo.toRealPath()
        val own = change.`package`.substringAfter(':')
        val found = mutableMapOf<String, List<Path>>()
        // Not a comparator: it would see the own artifact on either side, depending on the directory listing's order.
        val (first, rest) = artifacts.partition { it == own }
        try {
            for (artifact in first + rest.sorted()) {
                (localJars(change, artifact) ?: fetch(root, change, artifact))?.let { found[artifact] = it }
            }
        } catch (e: ToolError) {
            logger.info("Stopped fetching the old version of {}: {}", change.group(), e.message)
        }
        found
    }

    private fun localJars(
        change: DependencyChange,
        artifact: String,
    ) = localRepositoryArtifacts(localRepository, change.group(), change.fromVersion)[artifact]

    /**
     * Fetches one artifact at the old version into the local repository, without its dependencies, and returns its JARs,
     * or null if Maven can't get it. Fails with a [ToolError] if Maven times out or can't run.
     */
    private fun fetch(
        root: Path,
        change: DependencyChange,
        artifact: String,
    ): List<Path>? {
        val coordinates = "${change.group()}:$artifact:${change.fromVersion}"
        val goals =
            listOf(
                "${plugins.dependency}:get",
                "-N",
                "-Dartifact=$coordinates",
                "-Dtransitive=false",
                "-Dmaven.repo.local=$localRepository",
            )
        val result = maven(root, goals, FETCH_TIMEOUT)
        if (result.exitCode != 0) {
            logger.info("Can't fetch {} for the API changes: {}", coordinates, failure(result))
            return null
        }
        return localJars(change, artifact)
    }

    /** Runs Maven and collects the problems from its output and the test reports. */
    private fun problems(
        root: Path,
        goals: List<String>,
        timeout: Duration,
    ): List<Diagnostic> = problems(root, maven(root, goals, timeout))

    /** The problems in the output of the Maven run that left [result], and in the test reports. */
    private fun problems(
        root: Path,
        result: ProcessResult,
    ): List<Diagnostic> {
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
        return listOf(executable, "-B", "-fae", SKIP_SIGNING) + settingsOption
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
