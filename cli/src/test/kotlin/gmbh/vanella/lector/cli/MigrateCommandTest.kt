package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import gmbh.vanella.lector.core.BaseCommit
import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.Recipe
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.RecipeMatch
import gmbh.vanella.lector.core.RecipeRun
import gmbh.vanella.lector.core.RecipeRunner
import gmbh.vanella.lector.core.RecipeSelection
import gmbh.vanella.lector.core.ResolvedVersions
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.UpdateType
import gmbh.vanella.lector.core.runProcess
import gmbh.vanella.lector.ecosystems.jvm.MavenVersionScheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MigrateCommandTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var baseCheckout: Path

    private val parent = "org.springframework.boot:spring-boot-starter-parent"
    private val upgrade = Recipe("org.example.UpgradeSpringBoot_3_4", "org.example:recipes:1.0", "Apache License 2.0")
    private val selection = RecipeSelection(RecipeMatch(upgrade, "3.4.x", 1), emptyList())
    private val compileError = Diagnostic("src/App.java", 3, 5, null, "cannot find symbol", Severity.ERROR, "javac")
    private val changesAsked = mutableListOf<DependencyChange>()

    @BeforeTest
    fun `create a clean repository`() {
        git("init", "--quiet")
        repo.resolve("pom.xml").writeText("<parent>3.4.1</parent>\n")
        git("add", ".")
        git("commit", "--quiet", "-m", "head")
    }

    @Test
    fun `applies the recipe, then verifies, and prints the recipe section before the verification`() {
        val adapter = FakeAdapter()

        val result = command(adapter).test("$repo --package $parent --from 3.3.5 --to 3.4.1")

        assertTrue(result.stdout.startsWith("Recipe (applied)\n"), result.stdout)
        assertContains(result.stdout, "  Recipe: org.example.UpgradeSpringBoot_3_4, leaf 3.4.x at depth 1\n")
        assertContains(result.stdout, "  Changed files (1):\n    - pom.xml\n")
        assertContains(result.stdout, "    - $parent 3.4.1 -> 3.4.5\n")
        assertTrue(result.stdout.indexOf("Recipe (applied)") < result.stdout.indexOf("Caused by the update (0)"))
        assertEquals("", result.stderr)
        assertEquals(0, result.statusCode)
        assertEquals(1, adapter.verifyCalls)
        assertEquals("<parent>3.4.5</parent>\n", repo.resolve("pom.xml").readText(), "the recipe's change stays in the working tree")
        assertEquals("1", git("rev-list", "--count", "HEAD").trim(), "nothing is committed")
    }

    @Test
    fun `the change gets its ecosystem from the adapter and its update type from the adapter's version scheme`() {
        command(FakeAdapter()).test("$repo --package $parent --from 3.3.5 --to 3.4.1")

        assertEquals(listOf(DependencyChange("maven", parent, "3.3.5", "3.4.1", UpdateType.MINOR)), changesAsked)
    }

    @Test
    fun `a problem caused by the update exits with 1`() {
        val result = command(FakeAdapter(verify = listOf(compileError))).test("$repo --package $parent --from 3.3.5 --to 3.4.1")

        assertContains(result.stdout, "Caused by the update (1)")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `without a matching recipe it still verifies, and the exit code follows the verification`() {
        val adapter = FakeAdapter(verify = listOf(compileError))

        val result =
            command(
                adapter,
                index = { _, _ -> RecipeSelection(null, emptyList()) },
            ).test("$repo --package $parent --from 3.3.5 --to 3.4.1")

        assertContains(
            result.stdout,
            "Recipe (not applied)\n  Change: $parent 3.3.5 -> 3.4.1 (maven, minor)\n  Reason: no recipe matches\n\n",
        )
        assertEquals(1, adapter.verifyCalls)
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `a failed recipe run is reset, reported, and verify decides the exit code`() {
        val adapter = FakeAdapter(run = RecipeRun.Failed("plugin crashed"))

        val result = command(adapter).test("$repo --package $parent --from 3.3.5 --to 3.4.1")

        assertContains(result.stdout, "Reason: the recipe run failed: plugin crashed")
        assertEquals("", git("status", "--porcelain"))
        assertEquals(1, adapter.verifyCalls)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `an outdated PR runs no recipe and is reported with both versions`() {
        val adapter = FakeAdapter()

        val result = command(adapter).test("$repo --package $parent --from 3.3.5 --to 3.4.1 --base main")

        assertContains(result.stdout, "Reason: PR outdated: the base already resolves $parent to 3.4.9, higher than 3.4.1")
        assertEquals(0, adapter.recipeRuns)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `--format json prints the recipe section and the verification, and nothing else`() {
        val result =
            command(
                FakeAdapter(verify = listOf(compileError)),
            ).test("$repo --package $parent --from 3.3.5 --to 3.4.1 --format json")

        val json = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(listOf("recipe", "verification"), json.keys.toList())
        assertEquals(
            "applied",
            json
                .getValue("recipe")
                .jsonObject
                .getValue("outcome")
                .jsonPrimitive.content,
        )
        assertEquals(
            JsonReport.json(
                gmbh.vanella.lector.core
                    .Verification(causedByUpdate = listOf(compileError)),
            ),
            json.getValue("verification"),
        )
        assertEquals("", result.stderr)
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `a dirty working tree is a tool error with exit 2, and nothing runs or changes`() {
        repo.resolve("pom.xml").writeText("<parent>local edit</parent>\n")
        val adapter = FakeAdapter()

        val result = command(adapter).test("$repo --package $parent --from 3.3.5 --to 3.4.1")

        assertEquals("", result.stdout)
        assertContains(result.stderr, "Error: $repo has uncommitted changes. Commit or stash them first: pom.xml")
        assertEquals(2, result.statusCode)
        assertEquals(0, adapter.recipeRuns)
        assertEquals(0, adapter.verifyCalls)
        assertEquals("<parent>local edit</parent>\n", repo.resolve("pom.xml").readText())
    }

    @Test
    fun `a repository without Git is a tool error with exit 2`(
        @TempDir noGit: Path,
    ) {
        val result = command(FakeAdapter()).test("$noGit --package $parent --from 3.3.5 --to 3.4.1")

        assertEquals("", result.stdout)
        assertContains(result.stderr, "Error: git status failed in $noGit")
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `a repository no adapter supports is a tool error with exit 2`() {
        val result = command(FakeAdapter(detects = false)).test("$repo --package $parent --from 3.3.5 --to 3.4.1")

        assertEquals("", result.stdout)
        assertEquals("Error: No supported ecosystem in $repo. Supported: Maven (pom.xml at the root).\n", result.stderr)
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `an invalid --base is a tool error with exit 2`() {
        val adapter = FakeAdapter()

        val result =
            MigrateCommand(adapters = {
                listOf(adapter)
            }, index = { index() }).test("$repo --package $parent --from 3.3.5 --to 3.4.1 --base no-such-ref")

        assertEquals("", result.stdout)
        assertContains(result.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertEquals(2, result.statusCode)
        assertEquals(0, adapter.verifyCalls)
    }

    @Test
    fun `--package, --from, and --to are required, each a usage error with exit 2`() {
        listOf(
            "--from 3.3.5 --to 3.4.1" to "--package",
            "--package $parent --to 3.4.1" to "--from",
            "--package $parent --from 3.3.5" to "--to",
        ).forEach { (options, missing) ->
            val run = lector(FakeAdapter()).executeInProcess("migrate", repo.toString(), *options.split(" ").toTypedArray())

            assertEquals("", run.stdout)
            assertContains(run.stderr, "missing option $missing")
            assertEquals(2, run.exitCode)
        }
    }

    @Test
    fun `run through the root command, a problem caused by the update still exits with 1`() {
        val run =
            lector(FakeAdapter(verify = listOf(compileError)))
                .executeInProcess("migrate", repo.toString(), "--package", parent, "--from", "3.3.5", "--to", "3.4.1")

        assertContains(run.stdout, "Caused by the update (1)")
        assertEquals("", run.stderr)
        assertEquals(1, run.exitCode)
    }

    private fun lector(adapter: FakeAdapter) = LectorCommand("1.2.3").subcommands(command(adapter))

    private fun command(
        adapter: FakeAdapter,
        index: RecipeIndex = index(),
    ) = MigrateCommand(
        adapters = { listOf(adapter) },
        index = { index },
        baseCommit = { _, _ ->
            object : BaseCommit {
                override fun <T> checkout(block: (Path) -> T) = block(baseCheckout)
            }
        },
    )

    private fun index() =
        RecipeIndex { change, _ ->
            changesAsked += change
            selection
        }

    private fun git(vararg args: String): String {
        val command = listOf("git", "-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
        val result = runProcess(command + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }

    /**
     * Maven-like: the recipe moves the parent in `pom.xml` to 3.4.5, and the versions are read from that file. The base
     * checkout resolves the parent to 3.4.9.
     */
    private inner class FakeAdapter(
        private val detects: Boolean = true,
        private val verify: List<Diagnostic> = emptyList(),
        private val run: RecipeRun = RecipeRun.Applied,
    ) : EcosystemAdapter {
        var verifyCalls = 0
        var recipeRuns = 0

        override val ecosystem = "maven"
        override val versionScheme = MavenVersionScheme

        override val recipeRunner =
            RecipeRunner { dir, _ ->
                recipeRuns++
                dir.resolve("pom.xml").writeText("<parent>3.4.5</parent>\n")
                run
            }

        override val resolvedVersions =
            ResolvedVersions { dir ->
                if (dir == baseCheckout) {
                    mapOf(parent to "3.4.9")
                } else {
                    mapOf(
                        parent to
                            dir
                                .resolve("pom.xml")
                                .readText()
                                .substringAfter("<parent>")
                                .substringBefore("</parent>"),
                    )
                }
            }

        override fun detect(repo: Path) = detects

        override fun verify(repo: Path): List<Diagnostic> {
            verifyCalls++
            return verify
        }

        override fun runTests(
            repo: Path,
            tests: List<Diagnostic>,
        ) = emptyList<Diagnostic>()
    }
}
