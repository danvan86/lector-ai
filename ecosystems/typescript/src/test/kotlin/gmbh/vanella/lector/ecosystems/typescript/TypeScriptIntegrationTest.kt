package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.AttemptOutcome
import gmbh.vanella.lector.core.Budgets
import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Edit
import gmbh.vanella.lector.core.FixContext
import gmbh.vanella.lector.core.FixLoop
import gmbh.vanella.lector.core.FixRequest
import gmbh.vanella.lector.core.GitBaseCommit
import gmbh.vanella.lector.core.LlmClient
import gmbh.vanella.lector.core.Proposal
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.UpdateType
import gmbh.vanella.lector.core.attribute
import gmbh.vanella.lector.core.runProcess
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The TypeScript adapter with real Node, npm, pnpm, Yarn, tsc, Jest, and Vitest on the fixture repositories. Needs Node
 * and network access, so it is opt-in: `./gradlew :ecosystems:typescript:integrationTest`. The task puts the image's
 * Corepack first on `PATH` and points `LECTOR_JEST_JUNIT` at its jest-junit.
 */
@Tag("integration")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class TypeScriptIntegrationTest {
    @TempDir
    lateinit var dir: Path

    private val repo by lazy { dir.resolve("repo") }
    private val jestJunit = Path.of(checkNotNull(System.getenv("LECTOR_JEST_JUNIT")) { "run with :ecosystems:typescript:integrationTest" })
    private val adapter = TypeScriptAdapter(environment = System.getenv(), jestJunit = jestJunit)

    @AfterTest
    fun `close the adapter`() = adapter.close()

    @Test
    fun `each fixture installs, type-checks, and runs its tests, and leaves the working tree as it was`() {
        val testsRun = mapOf("npm-jest" to 2, "pnpm-workspace" to 1, "yarn-classic" to null, "yarn-berry-pnp" to 1)

        for ((fixture, tests) in testsRun) {
            fixture(fixture)

            assertTrue(adapter.detect(repo), fixture)
            assertEquals(emptyList(), adapter.verify(repo, 10.minutes), fixture)
            assertEquals(tests, adapter.testsRun(repo), fixture)
            assertEquals("", git("status", "--porcelain"), "$fixture: verify changes nothing Git sees, package.json included")
        }
    }

    @Test
    fun `pnpm runs the version packageManager pins, through Corepack`() {
        fixture("pnpm-workspace")

        adapter.verify(repo, 10.minutes)

        assertContains(repo.resolve("node_modules/.modules.yaml").readText(), "pnpm@10.34.6")
    }

    @Test
    fun `a type error and a failing test come with file, line, and the tools' own codes`() {
        fixture("npm-jest")
        val calc = repo.resolve("src/calc.ts")
        calc.writeText(calc.readText().replace("return a + b;", "return `${'$'}{a + b}`;"))

        val typeError = adapter.verify(repo, 10.minutes).single()

        assertEquals(
            listOf("src/calc.ts", "2", "3", "TS2322", "tsc"),
            listOf(typeError.file, "${typeError.line}", "${typeError.column}", typeError.code, typeError.tool),
        )

        calc.writeText(calc.readText().replace("return `${'$'}{a + b}`;", "return a - b;"))
        val failures = adapter.verify(repo, 10.minutes)

        assertEquals(listOf("src/calc.test.ts#calc > add > adds two numbers"), failures.map { it.test })
        assertEquals(listOf("src/calc.test.ts" to 6), failures.map { it.file to it.line })
        assertEquals(listOf("src/calc.test.ts#calc > add > adds two numbers"), adapter.runTests(repo, failures, 10.minutes).map { it.test })
    }

    @Test
    fun `a failing Vitest test in a workspace package is rerun alone, by its file and name`() {
        fixture("pnpm-workspace")
        val test = repo.resolve("packages/app/src/welcome.test.ts")
        test.writeText(test.readText().replace(".toBe(\"HELLO, ADA!\")", ".toBe(\"hello, ada!\")"))

        val failures = adapter.verify(repo, 10.minutes)

        assertEquals(listOf("packages/app/src/welcome.test.ts#welcome > shouts the greeting"), failures.map { it.test })
        assertEquals(failures.map { it.test }, adapter.runTests(repo, failures, 10.minutes).map { it.test })
    }

    @Test
    fun `a failure the update caused and one that was there before are told apart on the base commit`() {
        fixture("npm-jest")
        vendor("1.0.0", "a * 2")
        repo.resolve("src/double.test.ts").writeText(
            """
            import { double } from "calc";

            test("doubles", () => {
              expect(double(2)).toBe(4);
            });

            test("was broken before", () => {
              expect(double(1)).toBe(7);
            });
            """.trimIndent() + "\n",
        )
        dependOnCalc()
        git("add", "-A")
        git("commit", "--quiet", "-m", "base")
        val base = git("rev-parse", "HEAD").trim()
        vendor("2.0.0", "a * 2 + 1")
        lock()
        git("commit", "--quiet", "-am", "Update calc to 2.0.0")

        val verification = attribute(adapter, repo, GitBaseCommit.resolve(repo, base)) { 10.minutes }

        assertEquals(listOf("src/double.test.ts#doubles"), verification.causedByUpdate.map { it.test })
        assertEquals(listOf("src/double.test.ts#was broken before"), verification.preExisting.map { it.test })
    }

    @Test
    fun `grounding under Plug'n'Play reads the declarations from Yarn's zip archive`() {
        fixture("yarn-berry-pnp")
        adapter.verify(repo, 10.minutes)
        assertFalse(repo.resolve("node_modules").exists(), "Plug'n'Play installs no node_modules")
        val change = DependencyChange("npm", "dequal", "2.0.3", "2.0.3", UpdateType.OTHER)
        val error = Diagnostic("src/same.ts", 4, 10, "TS2554", "Expected 2 arguments, but got 1.", Severity.ERROR, "tsc")

        val grounding = adapter.ground(repo, change, listOf(error))

        assertContains(grounding, "// dequal/index.d.ts\nexport function dequal(foo: any, bar: any): boolean;")
    }

    @Test
    fun `a fix that edits package_json lets the lockfile follow, in the same commit`() {
        fixture("npm-jest")
        val calc = repo.resolve("src/calc.ts")
        calc.writeText("import { dequal } from \"dequal\";\n\n" + calc.readText() + "\nexport const same = dequal;\n")
        git("commit", "--quiet", "-am", "use dequal")
        val initial = attribute(adapter, repo, null) { 10.minutes }
        assertEquals(listOf("TS2307"), initial.causedByUpdate.map { it.code })
        val llm =
            Scripted(
                Edit(
                    "package.json",
                    "\"devDependencies\": {",
                    "\"dependencies\": {\n    \"dequal\": \"2.0.3\"\n  },\n  \"devDependencies\": {",
                ),
            )
        val change = DependencyChange("npm", "dequal", "2.0.2", "2.0.3", UpdateType.PATCH)

        val result = FixLoop(adapter, llm, null, Budgets(attempts = 1)).run(repo, FixContext(listOf(change), null, null), initial)

        assertIs<AttemptOutcome.Accepted>(result.attempts.single().outcome)
        assertEquals(
            listOf("package-lock.json", "package.json"),
            git("show", "--format=", "--name-only", "HEAD").lines().filter {
                it.isNotBlank()
            },
        )
        assertEquals(mapOf("dequal" to "2.0.3"), adapter.resolvedVersions.read(repo).filterKeys { it == "dequal" })
    }

    /** A model that answers once with [edit]. */
    private class Scripted(
        private val edit: Edit,
    ) : LlmClient {
        override fun fits(request: FixRequest) = true

        override fun propose(request: FixRequest) = Proposal.Edits(listOf(edit), "Depend on dequal")
    }

    /** The local package `calc` at [version], whose `double` computes [body]. */
    private fun vendor(
        version: String,
        body: String,
    ) {
        val dir = repo.resolve("vendor/calc").createDirectories()
        dir.resolve("package.json").writeText("""{"name": "calc", "version": "$version", "main": "index.js", "types": "index.d.ts"}""")
        dir.resolve("index.js").writeText("exports.double = (a) => $body;\n")
        dir.resolve("index.d.ts").writeText("export declare function double(a: number): number;\n")
    }

    /** Makes the fixture depend on the local `calc`. */
    private fun dependOnCalc() {
        val manifest = repo.resolve("package.json")
        manifest.writeText(
            manifest.readText().replace(
                "\"devDependencies\": {",
                "\"dependencies\": {\n    \"calc\": \"file:vendor/calc\"\n  },\n  \"devDependencies\": {",
            ),
        )
        lock()
    }

    /** Lets npm write the lockfile, as a bot's update does. */
    private fun lock() {
        val install = runProcess(listOf("npm", "install", "--package-lock-only", "--ignore-scripts"), repo, 5.minutes)
        assertEquals(0, install.exitCode, install.output)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun fixture(name: String) {
        if (repo.exists()) repo.toFile().deleteRecursively()
        val fixture = requireNotNull(javaClass.getResource("/fixtures/$name")).toURI().toPath()
        fixture.copyToRecursively(repo, followLinks = false, overwrite = false)
        // As a TypeScript project ignores what its tools write. Gradle leaves .gitignore files out of the test resources.
        repo.resolve(".gitignore").writeText("node_modules/\ndist/\n.yarn/*\n!.yarn/releases\n.pnp.*\n")
        git("init", "--quiet")
        git("add", "-A")
        git("commit", "--quiet", "-m", "fixture")
    }

    private fun git(vararg args: String): String {
        val command = listOf("git", "-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
        val result = runProcess(command + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }
}
