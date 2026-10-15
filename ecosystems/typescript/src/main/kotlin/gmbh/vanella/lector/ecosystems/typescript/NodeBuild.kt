package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.LockfileUpdate
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.ProcessTimedOut
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.ToolError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private val logger = LoggerFactory.getLogger(NodeBuild::class.java)

private val NODE_TIMEOUT = 1.hours
private val PNP_TIMEOUT = 2.minutes

// Prints where Plug'n'Play keeps each named package for an importer: `{"dequal": "/…/cache/dequal-npm-….zip/node_modules/dequal/"}`.
private const val PNP_SCRIPT =
    "const pnp = require('pnpapi'); const [issuer, ...names] = process.argv.slice(1); const found = {}; " +
        "for (const name of names) { try { found[name] = pnp.resolveToUnqualified(name, issuer); } catch {} } " +
        "console.log(JSON.stringify(found));"

// The lines of a failed process's output a problem shows: the first and the last half. npm prints its error first and
// its usage after it, pnpm and the test runners theirs last.
private const val OUTPUT_LINES = 20

// The variables of Node's package managers: registries, credentials, proxies, and Corepack's own (spec section 2), and
// the registry tokens an `.npmrc` reads by convention (`${NODE_AUTH_TOKEN}`, as setup-node writes it).
private val VARIABLE_PREFIXES = listOf("NPM_CONFIG_", "npm_config_", "YARN_", "COREPACK_")
private val VARIABLE_NAMES = setOf("NODE_AUTH_TOKEN", "NPM_TOKEN")

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
    private val variables = environment.filterKeys { name -> name in VARIABLE_NAMES || VARIABLE_PREFIXES.any { name.startsWith(it) } }

    // Per working tree: what the last install installed, whether the dependency builds ran since, and the test count.
    private val installed = mutableMapOf<Path, String>()
    private val built = mutableSetOf<Path>()
    private val testsRun = mutableMapOf<Path, Int?>()

    // The package whose runner ran each failed test: a root runner (Vitest or Jest projects) runs the workspace packages'
    // tests too. Test names are relative to the repository, so they hold on the base commit's checkout as well.
    private val ranIn = mutableMapOf<String, String>()

    private val tempDir = lazy { Files.createTempDirectory("lector-node") }
    private val temp by tempDir
    private var reports = 0

    /** Install, then stage 1 (the dependency builds and the type check), then, only if stage 1 is clean, stage 2 (the tests). */
    fun verify(
        repo: Path,
        timeout: Duration,
    ): List<Diagnostic> {
        testsRun.remove(repo.toRealPath())
        val session = session(repo, timeout).getOrElse { return listOf(unreadable(it)) }
        val install = session.install(frozen = true)
        val stage1 = install.ifEmpty { (session.build() + session.typeCheck()).distinct() }
        if (stage1.isNotEmpty()) return stage1
        val runs = session.project.packages.map { session.test(it, only = null) }
        session.project.packages
            .zip(runs)
            .forEach { (pkg, run) -> run.diagnostics.mapNotNull { it.test }.forEach { ranIn[it] = pkg.dir } }
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
        val session = session(repo, timeout).getOrElse { return listOf(unreadable(it)) }
        val install = session.install(frozen = true)
        val problems = install.ifEmpty { if (session.root !in built) session.build() else emptyList() }
        if (problems.isNotEmpty()) return problems
        // A failure of a whole package reruns its whole test run.
        return tests
            .mapNotNull { it.test }
            .groupBy { session.ranBy(it) }
            .flatMap { (pkg, ids) ->
                session
                    .test(
                        pkg,
                        ids.takeIf { id ->
                            id.none { it.startsWith(pkg.relative(PACKAGE_JSON) + "#") }
                        },
                    ).diagnostics
            }
    }

    /** Installs without the frozen flag, so the lockfile follows the edited `package.json` files. Returns the lockfile if it changed. */
    fun updateLockfiles(
        repo: Path,
        timeout: Duration,
    ): LockfileUpdate {
        val session = session(repo, timeout).getOrElse { return LockfileUpdate.Failed(unreadable(it).message) }
        val lockfile = session.root.resolve(session.project.manager.lockfile)
        val before = lockfile.readText()
        val failure = session.install(frozen = false)
        if (failure.isNotEmpty()) {
            return LockfileUpdate.Failed(
                failure.joinToString("\n") { listOfNotNull(it.file, it.message).joinToString(": ") },
            )
        }
        return LockfileUpdate.Updated(if (lockfile.readText() == before) emptyList() else listOf(session.project.manager.lockfile))
    }

    /**
     * Where Yarn's Plug'n'Play keeps the packages [names] for an importer in the directory [issuer] (spec section 5): one
     * `yarn node` call to the PnP API, the only Node call outside install, build, and tests. A location may be inside a
     * zip archive. Packages the importer can't reach are left out; so is everything if the call fails.
     */
    fun pnpLocations(
        repo: Path,
        issuer: Path,
        names: List<String>,
    ): Map<String, String> {
        val session =
            session(repo, PNP_TIMEOUT).getOrElse {
                logger.warn("Can't locate {} through Plug'n'Play: {}", names, it.message)
                return emptyMap()
            }
        val result =
            try {
                session.run(listOf("yarn", "node", "-e", PNP_SCRIPT, "$issuer/") + names, session.root)
            } catch (e: ProcessTimedOut) {
                ProcessResult(-1, e.message)
            }
        val json = result.output.lines().lastOrNull { it.startsWith("{") }
        // A line that starts with `{` and parses is an object.
        val locations =
            try {
                Json.parseToJsonElement(json.orEmpty()).jsonObject
            } catch (_: SerializationException) {
                null
            }
        if (result.exitCode != 0 || locations == null) logger.warn("Can't locate {} through Plug'n'Play: {}", names, result.output.trim())
        return locations.orEmpty().mapNotNull { (name, location) -> location.text()?.let { name to it } }.toMap()
    }

    /**
     * A session on [repo], or the [ToolError] that its project can't be read. `detect` read it before the run, so only an
     * edit of the fix loop or another commit, like the base's, makes it unreadable: a problem of that working tree.
     */
    private fun session(
        repo: Path,
        timeout: Duration,
    ): Result<Session> =
        try {
            Result.success(Session(repo.toRealPath(), timeout))
        } catch (e: ToolError) {
            Result.failure(e)
        }

    /** The problem that a working tree's project can't be read. */
    private fun unreadable(error: Throwable) =
        Diagnostic(null, null, null, null, "The project can't be read: ${error.message}", Severity.ERROR, ECOSYSTEM)

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

        /** The install, unless the last one in this working tree installed the same files. Returns its problems, if it failed. */
        fun install(frozen: Boolean): List<Diagnostic> {
            val fingerprint = fingerprint()
            if (frozen && installed[root] == fingerprint) return emptyList()
            installed.remove(root)
            built.remove(root)
            val command = commands.install(frozen)
            val result = run(command, root)
            // Lifecycle scripts build: a failed install yields their tsc errors, like a failed build script.
            if (result.exitCode !=
                0
            ) {
                return TscOutput.parse(result.output, root, root).ifEmpty { listOf(failure(command, project.root, result)) }
            }
            installed[root] = fingerprint()
            return emptyList()
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
                val name = commands.test().joinToString(" ")
                val failures = if (result.exitCode == 0) emptyList() else listOf(packageFailure(commands.test(), pkg, result, name))
                return TestRun(failures, null)
            }
            val report = temp.resolve("report-${++reports}.xml")
            val (arguments, environment) = runnerArguments(command, report, only.orEmpty(), pkg)
            val line = commands.exec(pkg, command.runner.bin, command.runner.bin, arguments)
            val result = run(line, pkg.dir(root), command.environment + environment)
            val failures = if (report.exists()) NodeTestReports.diagnostics(report, pkg, command.runner) else emptyList()
            val diagnostics =
                if (result.exitCode != 0 && failures.isEmpty()) listOf(packageFailure(line, pkg, result, command.runner.bin)) else failures
            return TestRun(diagnostics, if (report.exists()) NodeTestReports.count(report) else 0)
        }

        /**
         * A package's tests that failed without saying which: a test script that runs no runner the adapter knows, or a
         * runner that failed without a report (no tests found, a config it can't load). It is a test failure of the
         * whole package, named by its runner, `npm test` say, so it is rerun, and run on the base commit, like any other.
         */
        private fun packageFailure(
            command: List<String>,
            pkg: NodePackage,
            result: ProcessResult,
            runner: String,
        ) = failure(command, pkg, result, tool = runner.substringBefore(' '), test = "${pkg.relative(PACKAGE_JSON)}#$runner")

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
                    // Jest reads its file arguments as patterns on the absolute path, so each is anchored to the whole file
                    // name. They come first: an array option like --reporters takes the arguments after it.
                    files.map { "/${jsRegex(it)}$" } + command.arguments + reporters + filter to environment
                }

                Runner.VITEST -> {
                    listOf("run") + files + command.arguments +
                        listOf("--reporter=default", "--reporter=junit", "--outputFile.junit=$report") +
                        filter to emptyMap()
                }
            }
        }

        /** The package whose runner ran [test] in the last verify, else the one its file is in: the longest directory that contains it. */
        fun ranBy(test: String): NodePackage {
            val path = test.substringBefore('#')
            return project.packages.firstOrNull { it.dir == ranIn[test] }
                ?: project.packages.filter { it.dir.isEmpty() || path.startsWith("${it.dir}/") }.maxBy { it.dir.length }
        }

        fun run(
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
            // No prompts (rule 6): Corepack asks before a download, pnpm before it purges node_modules, unless CI is set.
            // The tool always runs as CI; a CI variable of the environment stays as it is.
            put("COREPACK_ENABLE_DOWNLOAD_PROMPT", "0")
            if ("CI" !in environment) put("CI", "true")
            val node = nodes.home(root, project.root.manifest.engines)
            if (node !=
                null
            ) {
                put("PATH", listOfNotNull(node.resolve("bin").toString(), environment["PATH"]).joinToString(File.pathSeparator))
            }
            if (npmrc != null) put("NPM_CONFIG_USERCONFIG", npmrc.toAbsolutePath().toString())
            // pnpm puts its store on the repository's file system if HOME is on another one, as in a container: not in the
            // user's working tree, unless the user says so.
            val home = environment["HOME"]
            if (project.manager == PackageManager.PNPM && home != null &&
                variables.keys.none { it.lowercase() == "npm_config_store_dir" }
            ) {
                put("npm_config_store_dir", "$home/.local/share/pnpm/store")
            }
            if (yarnrc != null && project.manager == PackageManager.YARN_BERRY) {
                yarnrc.copyTo(temp.resolve(".yarnrc.yml"), overwrite = true)
                put("HOME", temp.toString())
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
        val lines = result.output.lines().filter { it.isNotBlank() }
        val shown = if (lines.size <= OUTPUT_LINES) lines else lines.take(OUTPUT_LINES / 2) + "…" + lines.takeLast(OUTPUT_LINES / 2)
        val where = if (pkg.dir.isEmpty()) "" else " in ${pkg.dir}"
        val message =
            (
                listOf(
                    "`${command.joinToString(" ")}`$where failed with exit code ${result.exitCode}. Its output:",
                ) + shown
            ).joinToString("\n")
        return Diagnostic(null, null, null, null, message, Severity.ERROR, tool, test)
    }
}

/** [text] as a JavaScript regex that matches it literally. */
internal fun jsRegex(text: String) = text.replace(Regex("""[.*+?^${'$'}{}()|\[\]\\/]"""), """\\$0""")
