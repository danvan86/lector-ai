package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.LanguageSupport
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
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private val logger = LoggerFactory.getLogger(GradleAdapter::class.java)

private val GRADLE_TIMEOUT = 1.hours
private val STOP_TIMEOUT = 1.minutes
private const val OUTPUT_TAIL_LINES = 20

// The variables Gradle reads: its own options and user home, and project properties (`ORG_GRADLE_PROJECT_<name>`).
private val GRADLE_VARIABLES = listOf("GRADLE_", "ORG_GRADLE_PROJECT_")

private const val NO_CONFIGURATION_CACHE = "--no-configuration-cache"

private val ROOT_BUILD_FILES = listOf("settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts")

// Builds that need what the image doesn't have: the Android SDK, or Kotlin's other targets (M7 Q15).
private val UNSUPPORTED_PLUGIN = Regex("""com\.android\..+|org\.jetbrains\.kotlin\.multiplatform""")

private val OLDEST_SUPPORTED = GradleVersion(8, 0)

// Gradle 9.1 reads the toolchain settings as Gradle properties (`-D`); older versions as project properties (`-P`).
private val TOOLCHAINS_AS_GRADLE_PROPERTIES = GradleVersion(9, 1)

/**
 * Builds and tests Gradle projects (8.0 and newer), always through the project's wrapper, so the project's own Gradle
 * version runs, downloaded from its own `distributionUrl`. Every run gets lector-ai's init script and, if given, the
 * user's [init] script, for mirrors and credentials. Of the tool's [environment], Gradle gets its own variables:
 * `GRADLE_*` (the user home, say) and `ORG_GRADLE_PROJECT_*`, from which the build and the init script read credentials.
 *
 * JDKs: Gradle runs on the one [DaemonJdk] picks from the installed [jdks] (release → `JAVA_HOME`). The project's
 * toolchains resolve from the same JDKs, never by auto-detection or a download. A toolchain that isn't installed is a
 * tool error.
 *
 * The daemon stays on during a run, so the builds of the fix loop start fast. [close] stops it.
 *
 * Verify runs in two stages, both with `--continue`, so every project reports its problems: first the classes of every
 * source set, and only if that is clean, every `Test` task. Before the stages, the init script's query task reads the
 * model ([GradleModel]): it rejects Android and Kotlin Multiplatform builds, and grounding reads its compile classpath.
 *
 * Recipes run with the OpenRewrite Gradle plugin from [rewritePlugin], a directory of JARs that ships with the tool.
 * Without it, there is no recipe runner.
 */
class GradleAdapter(
    private val init: Path? = null,
    jdks: Map<Int, Path> = emptyMap(),
    environment: Map<String, String> = emptyMap(),
    rewritePlugin: Path? = null,
    private val run: (
        command: List<String>,
        workDir: Path,
        variables: Map<String, String>,
        timeout: Duration,
    ) -> ProcessResult = { command, workDir, variables, timeout ->
        runProcess(command, workDir, timeout, variables)
    },
) : EcosystemAdapter,
    AutoCloseable {
    private val installed = jdks.toSortedMap()
    private val variables = environment.filterKeys { name -> GRADLE_VARIABLES.any { name.startsWith(it) } }
    private val daemonJdk = DaemonJdk(installed)
    private val grounding = JavapGrounding(::classpathJars)

    // The repositories Gradle ran in, for close(), and the last model read in each, for grounding.
    private val roots = linkedSetOf<Path>()
    private val models = mutableMapOf<Path, GradleModel>()
    private val scripts = GradleInitScripts()

    override val ecosystem = "maven"
    override val versionScheme = MavenVersionScheme

    /**
     * Runs a recipe with the OpenRewrite Gradle plugin, applied by a second init script: no change to a build file. The
     * project's repositories provide the recipe artifact and the OpenRewrite libraries.
     */
    override val recipeRunner =
        rewritePlugin?.let { plugin ->
            RecipeRunner { repo, recipe ->
                val root = repo.toRealPath()
                val arguments =
                    listOf(
                        "-I",
                        scripts.rewrite(plugin).toString(),
                        "-Plector.recipe=${recipe.name}",
                        "-Plector.recipeArtifact=${recipe.artifact}",
                        "rewriteRun",
                        NO_CONFIGURATION_CACHE,
                    )
                val result = gradle(root, arguments)
                if (result.exitCode == 0) RecipeRun.Applied else RecipeRun.Failed(failure(result))
            }
        }

    /** Reads the versions Gradle resolves, with the query task. It compiles nothing. */
    override val resolvedVersions = ResolvedVersions { repo -> model(repo.toRealPath(), GRADLE_TIMEOUT).versions }

    override val languageSupport: LanguageSupport = JvmLanguageSupport

    /**
     * Whether [repo] has a Gradle build file at its root. Fails with a [ToolError] for a Gradle build this adapter can't
     * build: one with a `pom.xml` next to it, without a wrapper, or on Gradle 7 or older.
     */
    override fun detect(repo: Path): Boolean {
        if (ROOT_BUILD_FILES.none { repo.resolve(it).isRegularFile() }) return false
        val unsupported =
            when {
                repo.resolve("pom.xml").exists() -> {
                    "$repo has both a pom.xml and Gradle build files at the root. lector-ai doesn't pick a build tool: remove one of them."
                }

                !repo.resolve("gradlew").isRegularFile() -> {
                    "$repo has no Gradle wrapper (gradlew). lector-ai runs Gradle only through the wrapper, which names the Gradle version."
                }

                GradleWrapper.version(repo) < OLDEST_SUPPORTED -> {
                    "The wrapper runs Gradle ${GradleWrapper.version(repo)}. lector-ai supports Gradle 8 and newer."
                }

                else -> {
                    null
                }
            }
        if (unsupported != null) throw ToolError(unsupported)
        return true
    }

    override fun verify(
        repo: Path,
        timeout: Duration,
    ): List<Diagnostic> {
        val root = repo.toRealPath()
        val started = TimeSource.Monotonic.markNow()
        removeTestResults(root)
        readModel(root, timeout)
        return problems(root, listOf("lectorCompile", "--continue"), timeout - started.elapsedNow()).ifEmpty {
            problems(root, listOf("lectorTest", "--continue"), timeout - started.elapsedNow())
        }
    }

    /** The test cases in the reports that the last verify left. */
    override fun testsRun(repo: Path) = GRADLE_REPORTS.count(repo.toRealPath())

    /**
     * Reruns the classes of the given tests, each in the `Test` task that reported it. Whole classes, as for Maven. A
     * class that isn't there (on the base commit, say) doesn't fail the run.
     */
    override fun runTests(
        repo: Path,
        tests: List<Diagnostic>,
        timeout: Duration,
    ): List<Diagnostic> {
        val root = repo.toRealPath()
        removeTestResults(root)
        val tasks =
            tests.groupBy { it.tool }.flatMap { (task, failures) ->
                val classes = failures.mapNotNull { it.test }.map { it.substringBefore('#') }.distinct()
                listOf(task) + classes.flatMap { listOf("--tests", it) }
            }
        return problems(root, listOf("-Plector.runTests") + tasks + "--continue", timeout)
    }

    override fun ground(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ) = grounding.ground(repo, change, diagnostics)

    override fun clusterKey(diagnostic: Diagnostic) = ClusterKeys.of(diagnostic)

    override fun isBuildFile(file: String) = GradleScope.isBuildFile(file)

    override fun isEditable(file: String) = GradleScope.isEditable(file)

    override fun testDisablingConfig(
        file: String,
        content: String,
        line: Int,
    ) = GradleTestSkipping.reason(file, content, line)

    /**
     * Stops the Gradle daemons of every repository Gradle ran in that is still there, and removes the init scripts. A
     * removed base worktree needs no stop of its own: `--stop` stops every daemon of the same Gradle version.
     */
    override fun close() {
        for (root in roots.toList().filter { it.resolve("gradlew").isRegularFile() }) {
            val result =
                try {
                    gradle(root, listOf("--stop"), STOP_TIMEOUT)
                } catch (e: ToolError) {
                    ProcessResult(-1, e.message)
                }
            if (result.exitCode != 0) logger.warn("Can't stop the Gradle daemon of {}: {}", root, result.output.trim())
        }
        roots.clear()
        scripts.close()
    }

    /** Reads the model of [root] for grounding, and rejects builds this adapter can't build. A model Gradle can't read is left to the build to report. */
    private fun readModel(
        root: Path,
        timeout: Duration,
    ) {
        models.remove(root)
        try {
            models[root] = model(root, timeout)
        } catch (e: QueryFailed) {
            logger.info("Can't read the model of {}, so grounding has no JARs: {}", root, e.message)
        }
    }

    /** Runs the query task. Fails with [QueryFailed] if Gradle can't, and with a [ToolError] for an unsupported build. */
    private fun model(
        root: Path,
        timeout: Duration,
    ): GradleModel {
        val output = Files.createTempFile("lector-gradle-model", ".json")
        val model =
            try {
                val result = gradle(root, listOf("-Plector.queryOutput=$output", "lectorQuery", NO_CONFIGURATION_CACHE), timeout)
                if (result.exitCode != 0) throw QueryFailed(failure(result))
                GradleModel.parse(output.readText())
            } catch (e: ToolError) {
                output.deleteIfExists()
                throw e
            }
        output.deleteIfExists()
        return supported(model)
    }

    /** The [model], unless it is a build that needs more than the image has. */
    private fun supported(model: GradleModel): GradleModel {
        val plugin = model.plugins.firstOrNull { UNSUPPORTED_PLUGIN.matches(it) } ?: return model
        throw ToolError("The project applies the Gradle plugin $plugin. lector-ai doesn't support Android or Kotlin Multiplatform builds.")
    }

    /** The JARs of the change's group at its new version on the compile classpaths of the last model of [repo]. */
    private fun classpathJars(
        repo: Path,
        change: DependencyChange,
    ): List<Path> {
        val group = change.`package`.substringBefore(':')
        val model = models[repo.toRealPath()] ?: return emptyList()
        return model.classpath
            .filter { it.module.substringBefore(':') == group && it.version == change.toVersion && it.file.isRegularFile() }
            .map { it.file }
            .distinct()
            .sorted()
    }

    /** Runs Gradle and collects the problems from its output and the test reports. */
    private fun problems(
        root: Path,
        arguments: List<String>,
        timeout: Duration,
    ): List<Diagnostic> {
        val result = gradle(root, arguments, timeout)
        val problems = GradleConsole.parse(result.output, root) + GRADLE_REPORTS.parse(root)
        if (result.exitCode != 0 && problems.isEmpty()) return listOf(unexplainedFailure(result))
        return problems
    }

    /** Runs the wrapper in [root] with [arguments], on the daemon JDK, for at most [timeout] and never more than an hour. */
    private fun gradle(
        root: Path,
        arguments: List<String>,
        timeout: Duration = GRADLE_TIMEOUT,
    ): ProcessResult {
        val version = GradleWrapper.version(root)
        val environment =
            buildMap {
                daemonJdk.javaHome(root, version)?.let { put("JAVA_HOME", it.toString()) }
                putAll(variables)
            }
        roots.add(root)
        val result = run(command(root, version) + arguments, root, environment, minOf(timeout, GRADLE_TIMEOUT))
        GradleConsole.missingToolchain(result.output)?.let { java ->
            val jdks = if (installed.isEmpty()) "none are installed" else "only JDKs ${installed.keys.joinToString(", ")} are installed"
            throw ToolError("The project asks for a Java $java toolchain, but $jdks.")
        }
        return result
    }

    private fun command(
        root: Path,
        version: GradleVersion,
    ): List<String> {
        val userInit = init?.let { listOf("-I", it.toAbsolutePath().toString()) }.orEmpty()
        val flag = if (version >= TOOLCHAINS_AS_GRADLE_PROPERTIES) "-D" else "-P"
        val paths = if (installed.isEmpty()) emptyList() else listOf("paths=" + installed.values.joinToString(","))
        val toolchains = (listOf("auto-detect=false", "auto-download=false") + paths).map { "${flag}org.gradle.java.installations.$it" }
        return listOf(root.resolve("gradlew").toString(), "-I", scripts.lector().toString()) + userInit + "--console=plain" + toolchains
    }

    // A failed build must never look green, even when its output has no error this parser knows.
    private fun unexplainedFailure(result: ProcessResult) = Diagnostic(null, null, null, null, failure(result), Severity.ERROR, "gradle")

    private fun failure(result: ProcessResult): String {
        val tail =
            result.output
                .lines()
                .filter { it.isNotBlank() }
                .takeLast(OUTPUT_TAIL_LINES)
        return (listOf("Gradle failed with exit code ${result.exitCode}. The end of its output:") + tail).joinToString("\n")
    }

    /** Gradle couldn't run the query task, e.g. because a build script doesn't compile. */
    private class QueryFailed(
        message: String,
    ) : ToolError(message)
}

/** Removes the reports of earlier test runs, so only the ones of the next run are read. Gradle reruns a task whose reports are gone. */
@OptIn(ExperimentalPathApi::class)
private fun removeTestResults(root: Path) {
    Files
        .walk(root)
        .use { paths ->
            paths
                .asSequence()
                .filter { it.isDirectory() && it.endsWith(Path.of("build", "test-results")) }
                .toList()
        }.forEach { it.deleteRecursively() }
}
