package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.LockfileUpdate
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.runProcess
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.appendText
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** The command lines verify, runTests, and updateLockfiles run, on the fixtures, with a fake process runner. */
class NodeBuildTest {
    @TempDir
    lateinit var dir: Path

    private val repo by lazy { dir.resolve("repo") }

    /** One process the adapter started: its command, its directory relative to the repository, and its variables. */
    private data class Run(
        val command: List<String>,
        val dir: String,
        val environment: Map<String, String>,
        val timeout: Duration,
    )

    private val runs = mutableListOf<Run>()
    private val reportPath = Regex("""/[^ ]*/report-\d+\.xml""")
    private var answer: (Run) -> ProcessResult = { ProcessResult(0, "") }

    @Test
    fun `verify on the pnpm workspace installs frozen, builds lib before the type check, checks each package, then runs vitest`() {
        fixture("pnpm-workspace")

        assertEquals(emptyList(), build().verify(repo, 1.hours))

        assertEquals(
            listOf(
                "pnpm install --frozen-lockfile in .",
                "pnpm run build in packages/lib",
                "pnpm exec tsc --noEmit --pretty false -p tsconfig.json in packages/app",
                "pnpm exec tsc --noEmit --pretty false -p tsconfig.json in packages/lib",
                "pnpm exec vitest run --reporter=default --reporter=junit --outputFile.junit=<report> in packages/app",
            ),
            commands(),
            "the root's tsconfig.json compiles nothing, and app's build script doesn't run",
        )
    }

    @Test
    fun `with project references at the root, the type check is one tsc --build`() {
        fixture("pnpm-workspace")
        repo.resolve("tsconfig.json").writeText("""{"files": [], "references": [{"path": "packages/lib"}, {"path": "packages/app"}]}""")

        build().verify(repo, 1.hours)

        assertEquals(listOf("pnpm exec tsc --build --pretty false in ."), commands().filter { "tsc" in it })
    }

    @Test
    fun `tests run only if stage 1 has no errors`() {
        fixture("pnpm-workspace")
        answer = { run -> if ("tsc" in run.command && run.dir == "packages/app") ProcessResult(2, TSC_ERROR) else ProcessResult(0, "") }

        val diagnostics = build().verify(repo, 1.hours)

        assertEquals(listOf("packages/app/src/welcome.ts:1"), diagnostics.map { "${it.file}:${it.line}" })
        assertTrue(commands().none { "vitest" in it })
    }

    @Test
    fun `a failing build yields its tsc errors, or one problem without a location, and the type check still runs`() {
        fixture("pnpm-workspace")
        answer =
            { run -> if (run.command.last() == "build") ProcessResult(2, "> tsc -p tsconfig.json\n\n$TSC_ERROR") else ProcessResult(0, "") }

        assertEquals(listOf("packages/lib/src/welcome.ts"), build().verify(repo, 1.hours).map { it.file })
        assertEquals(2, commands().count { "tsc" in it })

        answer = { run -> if (run.command.last() == "build") ProcessResult(1, "sh: rollup: command not found") else ProcessResult(0, "") }
        val problem = build().verify(repo, 1.hours).single()
        assertEquals(
            Diagnostic(
                null,
                null,
                null,
                null,
                "`pnpm run build` in packages/lib failed with exit code 1. Its output:\nsh: rollup: command not found",
                Severity.ERROR,
                "pnpm",
            ),
            problem,
        )
    }

    @Test
    fun `a long output shows its first and its last lines, where the package managers print their errors`() {
        fixture("npm-jest")
        val output = listOf("npm error Invalid: lock file's vitest@3.2.7 does not satisfy vitest@4.1.11") + (1..30).map { "usage $it" }
        answer = { ProcessResult(1, output.joinToString("\n")) }

        val message = build().verify(repo, 1.hours).single().message

        assertEquals(listOf("`npm ci` failed with exit code 1. Its output:") + output.take(10) + "…" + output.takeLast(10), message.lines())
    }

    @Test
    fun `a tsc that fails without an error it reports is one problem`() {
        fixture("yarn-classic")
        answer = { run -> if ("tsc" in run.command) ProcessResult(1, "error Command \"tsc\" not found.") else ProcessResult(0, "") }

        val problem = build().verify(repo, 1.hours).single()

        assertEquals("tsc", problem.tool)
        assertEquals(null, problem.file)
        assertContains(problem.message, "`yarn run tsc --noEmit --pretty false -p tsconfig.json` failed with exit code 1.")
    }

    @Test
    fun `jest runs with jest-junit from the image path, and its failures come from the report`() {
        fixture("npm-jest")
        answer = { run ->
            val report = run.environment["JEST_JUNIT_OUTPUT_FILE"]
            if (report != null) output("jest-junit.xml").copyTo(Path.of(report))
            ProcessResult(if (report != null) 1 else 0, "")
        }
        val build = build()

        val diagnostics = build.verify(repo, 1.hours)

        assertEquals(
            listOf(
                "npm ci in .",
                "npm exec --no -- tsc --noEmit --pretty false -p tsconfig.json in .",
                "npm exec --no -- jest --ci --reporters=default --reporters=/opt/jest-junit in .",
            ),
            commands(),
        )
        assertEquals(
            mapOf(
                "JEST_JUNIT_ADD_FILE_ATTRIBUTE" to "true",
                "JEST_JUNIT_CLASSNAME" to "{classname}",
                "JEST_JUNIT_TITLE" to "{title}",
                "JEST_JUNIT_ANCESTOR_SEPARATOR" to " > ",
                "JEST_JUNIT_REPORT_TEST_SUITE_ERRORS" to "true",
            ),
            runs.last().environment.filterKeys { it.startsWith("JEST_JUNIT_") && it != "JEST_JUNIT_OUTPUT_FILE" },
        )
        assertEquals(
            listOf(
                "src/calc.test.ts#calc > add > fails on purpose",
                "src/broken.test.ts#Test suite failed to run > src/broken.test.ts",
            ),
            diagnostics.map {
                it.test
            },
        )
        assertEquals(5, build.testsRun(repo))
    }

    @Test
    fun `the test script's variables and arguments go to the runner, and a runner that fails without a report fails the package`() {
        fixture("npm-jest")
        edit("package.json") { it.replace("\"test\": \"jest\"", "\"test\": \"TZ=UTC jest --config jest.config.js\"") }
        answer = { run -> ProcessResult(if ("jest" in run.command) 1 else 0, "Error: Cannot find module 'ts-jest'") }

        val problem = build().verify(repo, 1.hours).single()

        assertEquals(
            "npm exec --no -- jest --config jest.config.js --ci --reporters=default --reporters=/opt/jest-junit in .",
            commands().last(),
        )
        assertEquals("UTC", runs.last().environment["TZ"])
        assertEquals("jest", problem.tool)
        assertEquals("package.json#jest", problem.test, "a failure of the package's whole test run")
    }

    @Test
    fun `without jest-junit, and for any other runner, the test script runs, and a failure is one problem without a location`() {
        fixture("yarn-classic")
        answer = { run -> ProcessResult(if (run.command == listOf("yarn", "test")) 1 else 0, "✖ shouts\nℹ fail 1") }
        val build = build()

        val problem = build.verify(repo, 1.hours).single()

        assertEquals("yarn test in .", commands().last())
        assertEquals(
            Diagnostic(
                null,
                null,
                null,
                null,
                "`yarn test` failed with exit code 1. Its output:\n✖ shouts\nℹ fail 1",
                Severity.ERROR,
                "yarn",
                "package.json#yarn test",
            ),
            problem,
        )
        assertEquals(null, build.testsRun(repo), "the script tells no count")

        fixture("npm-jest")
        runs.clear()
        build(jestJunit = null).verify(repo, 1.hours)
        assertEquals("npm test in .", commands().last())
    }

    @Test
    fun `runTests reruns only the given tests, the test file plus a name filter, in their package`() {
        fixture("pnpm-workspace")
        val build = build()
        build.verify(repo, 1.hours)
        runs.clear()
        val failed = test("packages/app/src/welcome.test.ts#welcome > shouts (the greeting)", "vitest")

        build.runTests(repo, listOf(failed, failed), 1.hours)

        assertEquals(
            listOf(
                "pnpm exec vitest run src/welcome.test.ts --reporter=default --reporter=junit --outputFile.junit=<report> " +
                    "-t ^(?:welcome shouts \\(the greeting\\))$ in packages/app",
            ),
            commands(),
            "a verified working tree is neither installed nor built again",
        )

        fixture("npm-jest")
        runs.clear()
        build().runTests(repo, listOf(test("src/calc.test.ts#calc > add > fails", "jest"), test("src/b.test.ts#top", "jest")), 1.hours)
        assertEquals(
            listOf(
                "npm ci in .",
                "npm exec --no -- jest src\\/calc\\.test\\.ts src\\/b\\.test\\.ts --ci --reporters=default --reporters=/opt/jest-junit " +
                    "-t ^(?:calc add fails|top)$ in .",
            ),
            commands(),
            "a working tree that wasn't verified, like the base commit's, is installed first",
        )
    }

    @Test
    fun `runTests reruns a test where its runner ran it, like a root Vitest with projects`() {
        fixture("pnpm-workspace")
        edit("package.json") { it.replace("\"private\": true,", "\"private\": true,\n  \"scripts\": {\"test\": \"vitest run\"},") }
        edit("packages/app/package.json") { it.replace(Regex(""",\s*"test": "vitest run""""), "") }
        answer = { run ->
            val report = run.command.firstOrNull { it.startsWith("--outputFile.junit=") }?.substringAfter('=')
            if (report != null) output("vitest-projects-junit.xml").copyTo(Path.of(report))
            ProcessResult(if (report != null) 1 else 0, "")
        }
        val build = build()

        val failed = build.verify(repo, 1.hours)
        runs.clear()
        build.runTests(repo, failed, 1.hours)

        assertEquals(listOf("packages/app/src/welcome.test.ts#welcome > shouts the greeting"), failed.map { it.test })
        assertEquals(listOf(6), failed.map { it.line }, "the failure names the place from the project")
        assertEquals(
            listOf(
                "pnpm exec vitest run packages/app/src/welcome.test.ts --reporter=default --reporter=junit --outputFile.junit=<report> " +
                    "-t ^(?:welcome shouts the greeting)$ in .",
            ),
            commands(),
        )
    }

    @Test
    fun `runTests of a runner's failure without a report runs the package's tests again, all of them`() {
        fixture("pnpm-workspace")

        build().runTests(
            repo,
            listOf(test("packages/app/package.json#vitest", "vitest"), test("packages/app/src/a.test.ts#x", "vitest")),
            1.hours,
        )

        assertEquals(
            "pnpm exec vitest run --reporter=default --reporter=junit --outputFile.junit=<report> in packages/app",
            commands().last(),
        )
    }

    @Test
    fun `runTests of a script's failure runs the script again, and stops at a failed install or build`() {
        fixture("yarn-classic")
        build().runTests(repo, listOf(test("package.json#yarn test", "yarn")), 1.hours)
        assertEquals(listOf("yarn install --frozen-lockfile in .", "yarn test in ."), commands())

        fixture("pnpm-workspace")
        runs.clear()
        answer = { run -> ProcessResult(if (run.command.last() == "build") 1 else 0, "") }
        val problems = build().runTests(repo, listOf(test("packages/app/src/welcome.test.ts#welcome > x", "vitest")), 1.hours)
        assertEquals(listOf("pnpm"), problems.map { it.tool })
        assertTrue(commands().none { "vitest" in it })

        runs.clear()
        answer = { run -> ProcessResult(if ("install" in run.command) 1 else 0, "ERR_PNPM_OUTDATED_LOCKFILE") }
        assertContains(build().runTests(repo, emptyList(), 1.hours).single().message, "`pnpm install --frozen-lockfile` failed")
    }

    @Test
    fun `the install runs again only when the lockfile or a package_json changed, and a failed install stops verify`() {
        fixture("pnpm-workspace")
        val build = build()
        build.verify(repo, 1.hours)
        build.verify(repo, 1.hours)
        assertEquals(1, commands().count { "install" in it })

        edit("packages/app/package.json") { it.replace("\"private\": true", "\"private\": true, \"description\": \"x\"") }
        build.verify(repo, 1.hours)
        assertEquals(2, commands().count { "install" in it })
        assertEquals(3, commands().count { it.startsWith("pnpm run build") }, "every verify builds: sources may have changed")

        runs.clear()
        answer = { run -> ProcessResult(if ("install" in run.command) 1 else 0, "ERR_PNPM_OUTDATED_LOCKFILE") }
        edit("pnpm-lock.yaml") { "$it\n" }
        val problem = build.verify(repo, 1.hours).single()
        assertEquals("pnpm", problem.tool)
        assertEquals(1, runs.size)
    }

    @Test
    fun `an install whose lifecycle scripts fail with tsc errors yields them, also when the lockfile follows an edit`() {
        fixture("npm-jest")
        val output = "> prepare\n> gulp build\n$TSC_ERROR\n[21:18:30] 'build' errored after 2.58 s\nnpm error code 1"
        answer = { run -> ProcessResult(if ("tsc" in run.command) 0 else 1, output) }

        assertEquals(listOf("src/welcome.ts:1"), build().verify(repo, 1.hours).map { "${it.file}:${it.line}" })
        assertEquals(
            LockfileUpdate.Failed("src/welcome.ts: Cannot find module '@fixture/lib' or its corresponding type declarations."),
            build().updateLockfiles(repo, 1.hours),
        )
    }

    @Test
    fun `a project an edit made unreadable is a problem of the attempt, not a tool error`() {
        fixture("npm-jest")
        val build = build()
        edit("package.json") { it.replace("\"private\": true,", "\"private\": true,,") }

        val problem = build.verify(repo, 1.hours).single()
        assertEquals(listOf(null, "npm"), listOf(problem.file, problem.tool))
        assertContains(problem.message, "Can't read package.json")
        assertContains(build.runTests(repo, listOf(test("src/calc.test.ts#calc > add", "jest")), 1.hours).single().message, "package.json")
        assertContains((build.updateLockfiles(repo, 1.hours) as LockfileUpdate.Failed).reason, "Can't read package.json")
        assertEquals(emptyMap(), build.pnpLocations(repo, repo, listOf("dequal")))
        assertEquals(emptyList(), runs, "nothing runs")
        assertEquals(null, build.testsRun(repo))

        fixture("npm-jest")
        repo.resolve(".nvmrc").writeText("26\n")
        assertContains(build(nodes = NodeSelection(mapOf(24 to node()))).verify(repo, 1.hours).single().message, "asks for Node 26")
    }

    @Test
    fun `updateLockfiles installs without the frozen flag and names the lockfile if it changed`() {
        fixture("pnpm-workspace")
        val build = build()
        answer = { run ->
            if ("--no-frozen-lockfile" in run.command) repo.resolve("pnpm-lock.yaml").appendText("\n# changed\n")
            ProcessResult(0, "")
        }

        assertEquals(LockfileUpdate.Updated(listOf("pnpm-lock.yaml")), build.updateLockfiles(repo, 1.hours))
        assertEquals(listOf("pnpm install --no-frozen-lockfile in ."), commands())
        build.verify(repo, 1.hours)
        assertEquals(1, commands().count { "install" in it }, "verify installs nothing after the update")

        answer = { ProcessResult(0, "") }
        assertEquals(LockfileUpdate.Updated(emptyList()), build.updateLockfiles(repo, 1.hours))

        answer = { ProcessResult(1, "ERR_PNPM_NO_MATCHING_VERSION No matching version found for vitest@99.0.0") }
        assertEquals(
            LockfileUpdate.Failed(
                "`pnpm install --no-frozen-lockfile` failed with exit code 1. Its output:\n" +
                    "ERR_PNPM_NO_MATCHING_VERSION No matching version found for vitest@99.0.0",
            ),
            build.updateLockfiles(repo, 1.hours),
        )
    }

    @Test
    fun `every process runs on the selected Node, with the package managers' variables, the npmrc, and no prompt`() {
        fixture("npm-jest")
        val node = node()
        val npmrc = dir.resolve("ci.npmrc").also { it.writeText("registry=https://npm.example.org/\n") }
        val environment =
            mapOf(
                "PATH" to "/usr/bin",
                "HOME" to "/home/ci",
                "NPM_CONFIG_REGISTRY" to "https://npm.example.org/",
                "npm_config_cache" to "/cache/npm",
                "YARN_NPM_AUTH_TOKEN" to "registry",
                "COREPACK_NPM_REGISTRY" to "https://npm.example.org/",
                "NODE_AUTH_TOKEN" to "registry",
                "NPM_TOKEN" to "registry",
                "GITHUB_TOKEN" to "secret",
                "LECTOR_GITHUB_TOKEN" to "secret",
                "INPUT_TOKEN" to "secret",
            )

        build(nodes = NodeSelection(mapOf(24 to node)), environment = environment, npmrc = npmrc).verify(repo, 1.hours)

        val variables = runs.map { it.environment.filterKeys { name -> !name.startsWith("JEST_JUNIT_") } }.distinct().single()
        assertEquals(
            mapOf(
                "NPM_CONFIG_REGISTRY" to "https://npm.example.org/",
                "npm_config_cache" to "/cache/npm",
                "YARN_NPM_AUTH_TOKEN" to "registry",
                "COREPACK_NPM_REGISTRY" to "https://npm.example.org/",
                "NODE_AUTH_TOKEN" to "registry",
                "NPM_TOKEN" to "registry",
                "COREPACK_ENABLE_DOWNLOAD_PROMPT" to "0",
                "CI" to "true",
                "PATH" to "${node.resolve("bin")}:/usr/bin",
                "NPM_CONFIG_USERCONFIG" to npmrc.toString(),
            ),
            variables,
        )
    }

    @Test
    fun `a child process sees neither the token nor any action input`() {
        fixture("yarn-classic")
        val tool =
            mapOf(
                "PATH" to System.getenv("PATH"),
                "LECTOR_GITHUB_TOKEN" to "secret",
                "INPUT_TOKEN" to "secret",
                "YARN_REGISTRY" to "r",
            )
        val outputs = mutableListOf<String>()

        NodeBuild(NodeSelection(emptyMap()), tool, null, null, null) { _, workDir, environment, timeout ->
            runProcess(listOf("env"), workDir, timeout, environment, inherited = tool).also { outputs += it.output }
        }.verify(repo, 1.minutes)

        assertTrue(outputs.isNotEmpty())
        assertTrue(outputs.all { "YARN_REGISTRY=r" in it && "secret" !in it && "INPUT_" !in it }, outputs.first())
    }

    @Test
    fun `the yarnrc is Yarn Berry's user-level config, in a home of its own, with Berry's cache where it was`() {
        fixture("yarn-berry-pnp")
        val yarnrc = dir.resolve("ci.yarnrc.yml").also { it.writeText("npmRegistryServer: https://npm.example.org\n") }
        val build = build(environment = mapOf("HOME" to "/home/ci"), yarnrc = yarnrc)

        build.verify(repo, 1.hours)

        val home = Path.of(requireNotNull(runs.first().environment["HOME"]))
        assertEquals(yarnrc.readText(), home.resolve(".yarnrc.yml").readText())
        assertEquals("/home/ci/.yarn/berry", runs.first().environment["YARN_GLOBAL_FOLDER"])
        assertEquals(
            listOf("yarn install --immutable in .", "yarn run tsc --noEmit --pretty false -p tsconfig.json in ."),
            commands().take(2),
            "under Plug'n'Play every tool runs through yarn",
        )
        build.close()
        assertFalse(home.exists())

        runs.clear()
        build(environment = mapOf("HOME" to "/home/ci", "YARN_GLOBAL_FOLDER" to "/cache/berry"), yarnrc = yarnrc).verify(repo, 1.hours)
        assertEquals("/cache/berry", runs.first().environment["YARN_GLOBAL_FOLDER"])
        build(yarnrc = yarnrc).verify(repo, 1.hours)
        assertFalse("YARN_GLOBAL_FOLDER" in runs.last().environment, "without a home, Berry keeps its default")

        fixture("npm-jest")
        runs.clear()
        build(yarnrc = yarnrc).verify(repo, 1.hours)
        assertFalse("HOME" in runs.first().environment, "only Yarn Berry reads it")
    }

    @Test
    fun `every process runs as CI, so that nothing prompts, and a CI value of the environment stays`() {
        fixture("yarn-classic")

        build().verify(repo, 1.hours)
        build(environment = mapOf("CI" to "1")).verify(repo, 1.hours)

        assertEquals("true", runs.first().environment["CI"])
        assertEquals(null, runs.last().environment["CI"], "the allowlist passes the environment's own")
    }

    @Test
    fun `pnpm's store stays in its default place under HOME, unless the user set one`() {
        fixture("pnpm-workspace")

        build(environment = mapOf("HOME" to "/home/ci")).verify(repo, 1.hours)
        assertEquals("/home/ci/.local/share/pnpm/store", runs.last().environment["npm_config_store_dir"])

        runs.clear()
        build(environment = mapOf("HOME" to "/home/ci", "NPM_CONFIG_STORE_DIR" to "/cache/pnpm")).verify(repo, 1.hours)
        assertEquals(null, runs.last().environment["npm_config_store_dir"])
        assertEquals("/cache/pnpm", runs.last().environment["NPM_CONFIG_STORE_DIR"])

        runs.clear()
        build().verify(repo, 1.hours)
        assertEquals(null, runs.last().environment["npm_config_store_dir"], "without HOME, pnpm's own default")

        fixture("npm-jest")
        runs.clear()
        build(environment = mapOf("HOME" to "/home/ci")).verify(repo, 1.hours)
        assertEquals(null, runs.last().environment["npm_config_store_dir"], "only pnpm has a store")
    }

    @Test
    fun `each process gets the time left, at most an hour`() {
        fixture("yarn-classic")

        build().verify(repo, 3.hours)
        build().verify(repo, 10.minutes)

        assertEquals(1.hours, runs.first().timeout)
        assertTrue(runs.last().timeout <= 10.minutes)
    }

    @Test
    fun `no test ran means the count is unknown`() {
        fixture("pnpm-workspace")
        edit("packages/app/package.json") { it.replace(Regex(""",\s*"test": "vitest run""""), "") }
        val build = build()

        build.verify(repo, 1.hours)

        assertEquals(null, build.testsRun(repo))
        assertEquals(null, build().testsRun(repo), "nor before any verify")
    }

    private fun build(
        nodes: NodeSelection = NodeSelection(emptyMap()),
        environment: Map<String, String> = emptyMap(),
        npmrc: Path? = null,
        yarnrc: Path? = null,
        jestJunit: Path? = Path.of("/opt/jest-junit"),
    ) = NodeBuild(nodes, environment, npmrc, yarnrc, jestJunit) { command, workDir, variables, timeout ->
        val run =
            Run(
                command,
                repo
                    .toRealPath()
                    .relativize(workDir)
                    .toString()
                    .ifEmpty { "." },
                variables,
                timeout,
            )
        runs += run
        answer(run)
    }

    /** The commands run so far, with the report path replaced, and their directory. */
    private fun commands() = runs.map { "${it.command.joinToString(" ").replace(reportPath, "<report>")} in ${it.dir}" }

    @OptIn(ExperimentalPathApi::class)
    private fun fixture(name: String) {
        if (repo.exists()) repo.toFile().deleteRecursively()
        requireNotNull(
            javaClass.getResource("/fixtures/$name"),
        ).toURI().toPath().copyToRecursively(repo, followLinks = false, overwrite = false)
    }

    private fun edit(
        file: String,
        change: (String) -> String,
    ) = repo.resolve(file).writeText(change(repo.resolve(file).readText()))

    private fun output(name: String) = requireNotNull(javaClass.getResource("/outputs/$name")).toURI().toPath()

    private fun test(
        id: String,
        tool: String,
    ) = Diagnostic(null, null, null, null, "failed", Severity.ERROR, tool, id)

    /** A Node installation with its version header, as the image has it. */
    private fun node(): Path {
        val home = dir.resolve("node-24")
        home.resolve("include/node").createDirectories().resolve("node_version.h").writeText(
            "#define NODE_MAJOR_VERSION 24\n#define NODE_MINOR_VERSION 21\n#define NODE_PATCH_VERSION 0\n",
        )
        return home
    }

    @Test
    fun `a package without a tsconfig_json isn't type-checked, and a diagnostic without a test is nothing to rerun`() {
        fixture("pnpm-workspace")
        repo.resolve("packages/lib/tsconfig.json").toFile().delete()
        val build = build()

        build.verify(repo, 1.hours)
        assertEquals(listOf("packages/app"), runs.filter { "tsc" in it.command }.map { it.dir })

        runs.clear()
        assertEquals(emptyList(), build.runTests(repo, listOf(test("x", "tsc").copy(test = null)), 1.hours))
        assertEquals(emptyList(), runs)
    }

    @Test
    fun `a package whose runner tells no count makes the count unknown, and a build that made no temp files closes cleanly`() {
        fixture("pnpm-workspace")
        edit("packages/app/package.json") { it.replace("\"test\": \"vitest run\"", "\"test\": \"node --test\"") }
        val build = build()

        build.verify(repo, 1.hours)

        assertEquals(null, build.testsRun(repo))
        build().close()
    }

    @Test
    fun `without the tool's PATH, the selected Node's is the whole PATH`() {
        fixture("yarn-classic")

        build(nodes = NodeSelection(mapOf(24 to node()))).verify(repo, 1.hours)

        assertEquals(dir.resolve("node-24/bin").toString(), runs.first().environment["PATH"])
    }

    private companion object {
        const val TSC_ERROR =
            "src/welcome.ts(1,23): error TS2307: Cannot find module '@fixture/lib' or its corresponding type declarations."
    }
}
