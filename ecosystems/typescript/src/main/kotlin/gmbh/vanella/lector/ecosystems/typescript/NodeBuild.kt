package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.LockfileUpdate
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.Severity
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

private val NODE_TIMEOUT = 1.hours
private const val OUTPUT_TAIL_LINES = 20

// The variables of Node's package managers: registries, credentials, proxies, and Corepack's own (spec section 2).
private val VARIABLE_PREFIXES = listOf("NPM_CONFIG_", "npm_config_", "YARN_", "COREPACK_")

/** Runs [command] in [workDir] with [environment] on top of the allowlisted one, for at most [timeout]. */
internal typealias ProcessRunner = (
    command: List<String>,
    workDir: Path,
    environment: Map<String, String>,
    timeout: Duration,
) -> ProcessResult

/**
 * Installs, builds, type-checks, and tests TypeScript repositories through their package manager (spec sections 2 to 4).
 *
 * - Every process runs on the Node [nodes] picks, first on `PATH`, with the package managers' variables of the tool's
 *   [environment], and never prompts for a Corepack download. [npmrc] is the user-level `.npmrc` of every run, [yarnrc]
 *   the user-level `.yarnrc.yml` of Yarn Berry, in a home directory of its own; Berry's global cache stays where it was.
 * - The install is frozen, and runs only when the lockfile or a `package.json` changed since the last install in that
 *   working tree. After a build-file edit, [updateLockfiles] installs without the frozen flag.
 * - Jest reports through [jestJunit], the image's `jest-junit`. Without it, Jest packages run their `test` script.
 */
internal class NodeBuild(
    private val nodes: NodeSelection,
    private val environment: Map<String, String>,
    private val npmrc: Path?,
    private val yarnrc: Path?,
    private val jestJunit: Path?,
    private val run: ProcessRunner,
) : AutoCloseable {
    private val variables = environment.filterKeys { name -> VARIABLE_PREFIXES.any { name.startsWith(it) } }

    // Per working tree: what the last install installed, whether the dependency builds ran since, and the test count.
    private val installed = mutableMapOf<Path, String>()
    private val built = mutableSetOf<Path>()
    private val testsRun = mutableMapOf<Path, Int?>()

    private val tempDir = lazy { Files.createTempDirectory("lector-node") }
    private val temp by tempDir
    private var reports = 0

    /** Install, then stage 1 (the dependency builds and the type check), then, only if stage 1 is clean, stage 2 (the tests). */
    fun verify(
        repo: Path,
        timeout: Duration,
    ): List<Diagnostic> {
        val session = Session(repo.toRealPath(), timeout)
        testsRun.remove(session.root)
        val install = session.install(frozen = true)
        val stage1 = if (install == null) (session.build() + session.typeCheck()).distinct() else listOf(install)
        if (stage1.isNotEmpty()) return stage1
        val runs = session.project.packages.map { session.test(it, only = null) }
        val counts = runs.map { it.count }
        testsRun[session.root] = if (null in counts) null else counts.filterNotNull().sum()
        return runs.flatMap { it.diagnostics }
    }

    /** The test cases the last verify of [repo] ran, or null if a package's runner reports no count or verify ran no test. */
    fun testsRun(repo: Path) = testsRun[repo.toRealPath()]?.takeIf { it > 0 }

    /**
     * Reruns only [tests], each in its package: Jest and Vitest with their files and names, other runners with the whole
     * `test` script. A working tree that wasn't verified, like the base commit's, is installed and built first.
     */
    fun runTests(
        repo: Path,
        tests: List<Diagnostic>,
        timeout: Duration,
    ): List<Diagnostic> {
        val session = Session(repo.toRealPath(), timeout)
        val install = session.install(frozen = true)
        val problems = if (install == null && session.root !in built) session.build() else listOfNotNull(install)
        if (problems.isNotEmpty()) return problems
        return tests
            .mapNotNull { it.test }
            .groupBy { session.owner(it.substringBefore('#')) }
            .flatMap { (pkg, ids) -> session.test(pkg, ids).diagnostics }
    }

    /** Installs without the frozen flag, so the lockfile follows the edited `package.json` files. Returns the lockfile if it changed. */
    fun updateLockfiles(
        repo: Path,
        timeout: Duration,
    ): LockfileUpdate {
        val session = Session(repo.toRealPath(), timeout)
        val lockfile = session.root.resolve(session.project.manager.lockfile)
        val before = lockfile.readText()
        val failure = session.install(frozen = false)
        if (failure != null) return LockfileUpdate.Failed(failure.message)
        return LockfileUpdate.Updated(if (lockfile.readText() == before) emptyList() else listOf(session.project.manager.lockfile))
    }

    @OptIn(ExperimentalPathApi::class)
    override fun close() {
        if (tempDir.isInitialized()) temp.deleteRecursively()
    }

    /** The tests one package ran: their failures, and how many ran, or null if its runner doesn't tell. */
    private class TestRun(
        val diagnostics: List<Diagnostic>,
        val count: Int?,
    )

    /** One call on one working tree, with its project, its environment, and its deadline. */
    private inner class Session(
        val root: Path,
        timeout: Duration,
    ) {
        val project = NodeProject.read(root)
        private val commands = Commands(project.manager)
        private val deadline = TimeSource.Monotonic.markNow() + timeout
        private val variables = variables(root, project)

        /** The install, unless the last one in this working tree installed the same files. Returns its failure, or null. */
        fun install(frozen: Boolean): Diagnostic? {
            val fingerprint = fingerprint()
            if (frozen && installed[root] == fingerprint) return null
            installed.remove(root)
            built.remove(root)
            val command = commands.install(frozen)
            val result = run(command, root)
            if (result.exitCode != 0) return failure(command, project.root, result)
            installed[root] = fingerprint()
            return null
        }

        /** Stage 1, step 1: the `build` script of every workspace package another one depends on, in dependency order. */
        fun build(): List<Diagnostic> {
            val diagnostics =
                dependencyBuilds(project).flatMap { pkg ->
                    val command = commands.script("build")
                    val result = run(command, pkg.dir(root))
                    if (result.exitCode ==
                        0
                    ) {
                        emptyList()
                    } else {
                        TscOutput.parse(result.output, root, pkg.dir(root)).ifEmpty { listOf(failure(command, pkg, result)) }
                    }
                }
            built.add(root)
            return diagnostics
        }

        /** Stage 1, step 2: `tsc --build` with project references at the root, otherwise `tsc --noEmit` per package. */
        fun typeCheck(): List<Diagnostic> {
            if (Tsconfig.read(root.resolve(TSCONFIG)).references) return tsc(project.root, listOf("--build", "--pretty", "false"))
            return project.packages
                .filter { it.hasTsconfig && !Tsconfig.read(it.dir(root).resolve(TSCONFIG)).compilesNothing }
                .flatMap { tsc(it, listOf("--noEmit", "--pretty", "false", "-p", TSCONFIG)) }
        }

        private fun tsc(
            pkg: NodePackage,
            arguments: List<String>,
        ): List<Diagnostic> {
            val command = commands.exec(pkg, "typescript", "tsc", arguments)
            val result = run(command, pkg.dir(root))
            val errors = TscOutput.parse(result.output, root, pkg.dir(root))
            return if (result.exitCode != 0 && errors.isEmpty()) listOf(failure(command, pkg, result, tool = "tsc")) else errors
        }

        /** Stage 2 for [pkg]: its tests, or [only] the tests with these names. A package without a `test` script has none. */
        fun test(
            pkg: NodePackage,
            only: List<String>?,
        ): TestRun {
            val script = pkg.manifest.scripts["test"] ?: return TestRun(emptyList(), 0)
            val command = TestScripts.command(script)?.takeIf { it.runner == Runner.VITEST || jestJunit != null }
            if (command == null) {
                val result = run(commands.test(), pkg.dir(root))
                val test = "${pkg.relative(PACKAGE_JSON)}#${commands.test().joinToString(" ")}"
                val failures = if (result.exitCode == 0) emptyList() else listOf(failure(commands.test(), pkg, result, test = test))
                return TestRun(failures, null)
            }
            val report = temp.resolve("report-${++reports}.xml")
            val (arguments, environment) = runnerArguments(command, report, only.orEmpty(), pkg)
            val line = commands.exec(pkg, command.runner.bin, command.runner.bin, arguments)
            val result = run(line, pkg.dir(root), command.environment + environment)
            val failures = if (report.exists()) NodeTestReports.diagnostics(report, pkg, command.runner) else emptyList()
            val diagnostics =
                if (result.exitCode != 0 &&
                    failures.isEmpty()
                ) {
                    listOf(failure(line, pkg, result, tool = command.runner.bin))
                } else {
                    failures
                }
            return TestRun(diagnostics, if (report.exists()) NodeTestReports.count(report) else 0)
        }

        /** The runner's arguments and variables: the script's, the reporter's, and the files and names of [tests], if any. */
        private fun runnerArguments(
            command: TestCommand,
            report: Path,
            tests: List<String>,
            pkg: NodePackage,
        ): Pair<List<String>, Map<String, String>> {
            val files = tests.map { it.substringBefore('#').removePrefix(pkg.relative("")) }.distinct()
            val names = tests.map { jsRegex(it.substringAfter('#').replace(NAME_SEPARATOR, " ")) }.distinct()
            val filter = if (tests.isEmpty()) emptyList() else listOf("-t", "^(?:${names.joinToString("|")})$")
            return when (command.runner) {
                Runner.JEST -> {
                    val reporters = listOf("--ci", "--reporters=default", "--reporters=$jestJunit")
                    val environment =
                        mapOf(
                            "JEST_JUNIT_OUTPUT_FILE" to report.toString(),
                            "JEST_JUNIT_ADD_FILE_ATTRIBUTE" to "true",
                            "JEST_JUNIT_CLASSNAME" to "{classname}",
                            "JEST_JUNIT_TITLE" to "{title}",
                            "JEST_JUNIT_ANCESTOR_SEPARATOR" to NAME_SEPARATOR,
                            "JEST_JUNIT_REPORT_TEST_SUITE_ERRORS" to "true",
                        )
                    // Jest reads its file arguments as patterns.
                    command.arguments + reporters + files.map(::jsRegex) + filter to environment
                }

                Runner.VITEST -> {
                    listOf("run") + command.arguments + listOf("--reporter=default", "--reporter=junit", "--outputFile.junit=$report") +
                        files + filter to emptyMap()
                }
            }
        }

        /** The package [path] (relative to the root) is in: the one with the longest directory that contains it. */
        fun owner(path: String) = project.packages.filter { it.dir.isEmpty() || path.startsWith("${it.dir}/") }.maxBy { it.dir.length }

        private fun run(
            command: List<String>,
            dir: Path,
            extra: Map<String, String> = emptyMap(),
        ): ProcessResult {
            val left = deadline.elapsedNow().unaryMinus()
            return run(command, dir, variables + extra, minOf(left, NODE_TIMEOUT))
        }

        /** What changes an install: the lockfile and every `package.json`. */
        private fun fingerprint(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val files = listOf(root.resolve(project.manager.lockfile)) + project.packages.map { it.dir(root).resolve(PACKAGE_JSON) }
            files.forEach { digest.update(it.readBytes()) }
            return HexFormat.of().formatHex(digest.digest())
        }
    }

    private fun variables(
        root: Path,
        project: NodeProject,
    ): Map<String, String> =
        buildMap {
            putAll(variables)
            put("COREPACK_ENABLE_DOWNLOAD_PROMPT", "0")
            val node = nodes.home(root, project.root.manifest.engines)
            if (node !=
                null
            ) {
                put("PATH", listOfNotNull(node.resolve("bin").toString(), environment["PATH"]).joinToString(File.pathSeparator))
            }
            if (npmrc != null) put("NPM_CONFIG_USERCONFIG", npmrc.toAbsolutePath().toString())
            if (yarnrc != null && project.manager == PackageManager.YARN_BERRY) {
                yarnrc.copyTo(temp.resolve(".yarnrc.yml"), overwrite = true)
                put("HOME", temp.toString())
                val home = environment["HOME"]
                if (home != null && "YARN_GLOBAL_FOLDER" !in variables) put("YARN_GLOBAL_FOLDER", "$home/.yarn/berry")
            }
        }

    // A build or test that fails without telling why must never look green.
    private fun failure(
        command: List<String>,
        pkg: NodePackage,
        result: ProcessResult,
        tool: String = command.first(),
        test: String? = null,
    ): Diagnostic {
        val tail =
            result.output
                .lines()
                .filter { it.isNotBlank() }
                .takeLast(OUTPUT_TAIL_LINES)
        val where = if (pkg.dir.isEmpty()) "" else " in ${pkg.dir}"
        val message =
            (
                listOf("`${command.joinToString(" ")}`$where failed with exit code ${result.exitCode}. The end of its output:") +
                    tail
            ).joinToString("\n")
        return Diagnostic(null, null, null, null, message, Severity.ERROR, tool, test)
    }
}

/** [text] as a JavaScript regex that matches it literally. */
internal fun jsRegex(text: String) = text.replace(Regex("""[.*+?^${'$'}{}()|\[\]\\/]"""), """\\$0""")
