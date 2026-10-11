package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RecipeStepTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var baseCheckout: Path

    private val boot = "org.example:boot"
    private val change = DependencyChange("test", boot, "3.3.5", "3.4.1", UpdateType.MINOR)
    private val recipe = Recipe("UpgradeBoot_3_4", "org.example:recipes:1.0", "Apache License 2.0")
    private val match = RecipeMatch(recipe, "3.4.x", 1)
    private val other = RecipeMatch(recipe.copy(name = "UpgradeCloud"), "3.4.x", 2)
    private val selection = RecipeSelection(match, listOf(other))
    private val index = RecipeIndex { _, _ -> selection }

    @BeforeTest
    fun `create a clean repository`() {
        git("init", "--quiet")
        repo.resolve("build.txt").writeText("boot 3.4.1\n")
        git("add", ".")
        git("commit", "--quiet", "-m", "head")
    }

    @Test
    fun `a successful recipe stays in the working tree, with its files and version changes`() {
        val adapter =
            FakeAdapter(
                before = mapOf(boot to "3.4.1", "org.example:web" to "6.1.0", "org.example:gone" to "1.0"),
                after = mapOf(boot to "3.4.5", "org.example:web" to "6.2.1", "org.example:new" to "2.0"),
                run = { dir ->
                    dir.resolve("build.txt").writeText("boot 3.4.5\n")
                    dir.resolve("Added.java").writeText("class Added {}\n")
                    RecipeRun.Applied
                },
            )

        val result = applyRecipe(change, adapter, index, repo, base = null)

        val expected =
            RecipeOutcome.Applied(
                changedFiles = listOf("Added.java", "build.txt"),
                versionChanges =
                    listOf(
                        VersionChange(boot, "3.4.1", "3.4.5"),
                        VersionChange("org.example:gone", "1.0", null),
                        VersionChange("org.example:new", null, "2.0"),
                        VersionChange("org.example:web", "6.1.0", "6.2.1"),
                    ),
            )
        assertEquals(RecipeResult(selection, expected), result)
        assertEquals("boot 3.4.5\n", repo.resolve("build.txt").readText())
        assertEquals(listOf(recipe), adapter.runs)
        assertEquals(1, commits())
    }

    @Test
    fun `a change that is not an upgrade runs no recipe`() {
        val adapter = FakeAdapter()

        val result = applyRecipe(change.copy(toVersion = "3.3.5"), adapter, index, repo, base = null)

        assertEquals(RecipeResult(RecipeSelection.NONE, RecipeOutcome.NotAnUpgrade), result)
        assertTrue(adapter.runs.isEmpty())
    }

    @Test
    fun `a base that already has a higher version makes the PR outdated`() {
        val adapter = FakeAdapter(base = mapOf(boot to "3.4.5"))

        val result = applyRecipe(change, adapter, index, repo, FakeBase(baseCheckout))

        assertEquals(RecipeResult(RecipeSelection.NONE, RecipeOutcome.PrOutdated("3.4.5", "3.4.1")), result)
        assertTrue(adapter.runs.isEmpty())
    }

    @Test
    fun `a base with the same or a lower version, or without the package, is not outdated`() {
        listOf(mapOf(boot to "3.4.1"), mapOf(boot to "3.3.5"), emptyMap()).forEach { base ->
            val adapter = FakeAdapter(base = base)

            val result = applyRecipe(change, adapter, index, repo, FakeBase(baseCheckout))

            assertTrue(result.outcome is RecipeOutcome.Applied, "base $base: $result")
        }
    }

    @Test
    fun `no matching recipe runs nothing`() {
        val adapter = FakeAdapter()
        val empty = RecipeSelection(null, emptyList())

        val result = applyRecipe(change, adapter, { _, _ -> empty }, repo, base = null)

        assertEquals(RecipeResult(empty, RecipeOutcome.NoMatch), result)
    }

    @Test
    fun `the index gets the change and the adapter's version scheme`() {
        var asked: Pair<DependencyChange, VersionScheme>? = null

        applyRecipe(change, FakeAdapter(), { c, v -> selection.also { asked = c to v } }, repo, base = null)

        assertEquals(change to DottedVersions, asked)
    }

    @Test
    fun `an ecosystem without a recipe runner runs nothing`() {
        val result = applyRecipe(change, FakeAdapter().apply { recipeRunner = null }, index, repo, base = null)

        assertEquals(RecipeResult(selection, RecipeOutcome.NoRecipeRunner), result)
    }

    @Test
    fun `an ecosystem without resolved versions can't prove there is no downgrade`() {
        val adapter = FakeAdapter().apply { resolvedVersions = null }

        val result = applyRecipe(change, adapter, index, repo, base = null)

        assertEquals(RecipeResult(selection, RecipeOutcome.VersionsUnreadable("test can't read resolved versions")), result)
        assertTrue(adapter.runs.isEmpty())
    }

    @Test
    fun `an outdated PR can't be detected without resolved versions`() {
        val result = applyRecipe(change, FakeAdapter().apply { resolvedVersions = null }, index, repo, FakeBase(baseCheckout))

        assertEquals(RecipeResult(RecipeSelection.NONE, RecipeOutcome.VersionsUnreadable("test can't read resolved versions")), result)
    }

    @Test
    fun `unreadable base versions run no recipe`() {
        val adapter = FakeAdapter(unreadable = setOf(baseCheckout))

        val result = applyRecipe(change, adapter, index, repo, FakeBase(baseCheckout))

        assertEquals(RecipeResult(RecipeSelection.NONE, RecipeOutcome.VersionsUnreadable("resolution failed in $baseCheckout")), result)
    }

    @Test
    fun `unreadable versions before the recipe run no recipe`() {
        val adapter = FakeAdapter(unreadable = setOf(repo))

        val result = applyRecipe(change, adapter, index, repo, base = null)

        assertEquals(RecipeResult(selection, RecipeOutcome.VersionsUnreadable("resolution failed in $repo")), result)
        assertTrue(adapter.runs.isEmpty())
    }

    @Test
    fun `unreadable versions after the recipe discard its result`() {
        val adapter = FakeAdapter(run = { dir -> breakVersions(dir) })

        val result = applyRecipe(change, adapter, index, repo, base = null)

        assertEquals(RecipeResult(selection, RecipeOutcome.VersionsUnreadable("resolution failed in $repo")), result)
        assertClean()
    }

    @Test
    fun `a failed run is reset and reported`() {
        val adapter =
            FakeAdapter(run = { dir ->
                dir.resolve("build.txt").writeText("half done\n")
                dir.resolve("Stray.java").writeText("class Stray {}\n")
                RecipeRun.Failed("plugin crashed")
            })

        val result = applyRecipe(change, adapter, index, repo, base = null)

        assertEquals(RecipeResult(selection, RecipeOutcome.RunFailed("plugin crashed")), result)
        assertClean()
    }

    @Test
    fun `a runner that throws a tool error counts as a failed run`() {
        val adapter = FakeAdapter(run = { throw ToolError("mvn did not finish within 1h") })

        val result = applyRecipe(change, adapter, index, repo, base = null)

        assertEquals(RecipeResult(selection, RecipeOutcome.RunFailed("mvn did not finish within 1h")), result)
        assertClean()
    }

    @Test
    fun `a recipe that downgrades a package is discarded`() {
        val adapter =
            FakeAdapter(
                before = mapOf(boot to "3.4.1", "org.example:web" to "6.2.1"),
                after = mapOf(boot to "3.4.1", "org.example:web" to "6.1.0"),
                run = { dir ->
                    dir.resolve("build.txt").writeText("web 6.1.0\n")
                    RecipeRun.Applied
                },
            )

        val result = applyRecipe(change, adapter, index, repo, base = null)

        val downgrade = VersionChange("org.example:web", "6.2.1", "6.1.0")
        assertEquals(RecipeResult(selection, RecipeOutcome.DowngradeDetected(listOf(downgrade))), result)
        assertClean()
    }

    private fun breakVersions(dir: Path): RecipeRun {
        dir.resolve("build.txt").writeText("unreadable\n")
        return RecipeRun.Applied
    }

    private fun assertClean() {
        assertEquals("", git("status", "--porcelain"))
        assertEquals("boot 3.4.1\n", repo.resolve("build.txt").readText())
    }

    private fun commits() = git("rev-list", "--count", "HEAD").trim().toInt()

    private fun git(vararg args: String): String {
        val command = listOf("git", "-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
        val result = runProcess(command + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }

    /**
     * Versions: [before] until the recipe ran, [after] once it did, [base] in the base checkout. Reading fails in the
     * directories in [unreadable], and after a recipe that wrote `unreadable` into `build.txt`.
     */
    private inner class FakeAdapter(
        private val before: Map<String, String> = mapOf(boot to "3.4.1"),
        private val after: Map<String, String> = before,
        private val base: Map<String, String> = emptyMap(),
        private val unreadable: Set<Path> = emptySet(),
        private val run: (Path) -> RecipeRun = { RecipeRun.Applied },
    ) : EcosystemAdapter {
        val runs = mutableListOf<Recipe>()

        override val ecosystem = "test"
        override val versionScheme = DottedVersions

        override var recipeRunner: RecipeRunner? =
            RecipeRunner { dir, recipe ->
                runs += recipe
                run(dir)
            }

        override var resolvedVersions: ResolvedVersions? =
            ResolvedVersions { dir ->
                if (dir in unreadable || (dir == repo && repo.resolve("build.txt").readText() == "unreadable\n")) {
                    throw ToolError("resolution failed in $dir")
                }
                when {
                    dir == baseCheckout -> base
                    runs.isEmpty() -> before
                    else -> after
                }
            }

        override fun detect(repo: Path) = true

        override fun verify(repo: Path) = emptyList<Diagnostic>()

        override fun runTests(
            repo: Path,
            tests: List<Diagnostic>,
        ) = emptyList<Diagnostic>()
    }

    private class FakeBase(
        private val checkout: Path,
    ) : BaseCommit {
        override fun <T> checkout(block: (Path) -> T) = block(checkout)
    }
}
