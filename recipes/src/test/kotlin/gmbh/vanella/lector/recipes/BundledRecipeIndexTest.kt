package gmbh.vanella.lector.recipes

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Recipe
import gmbh.vanella.lector.core.RecipeMatch
import gmbh.vanella.lector.core.UpdateType
import gmbh.vanella.lector.core.VersionScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BundledRecipeIndexTest {
    private val parent = "org.springframework.boot:spring-boot-starter-parent"
    private val change = DependencyChange("maven", parent, "3.3.5", "3.4.1", UpdateType.MINOR)

    @Test
    fun `finds the recipe whose leaf contains the target version`() {
        val index = index(entry("UpgradeSpringBoot_3_4", "3.4.x"))

        val selection = index.find(change, Dotted)

        assertEquals(match("UpgradeSpringBoot_3_4", "3.4.x"), selection.chosen)
        assertEquals(emptyList(), selection.alternatives)
    }

    @Test
    fun `a recipe's target is its highest leaf, so a recipe that goes further doesn't match`() {
        val index =
            index(
                entry("UpgradeSpringBoot_3_3", "3.3.x"),
                entry("UpgradeSpringBoot_3_4", "3.4.x"),
                entry("UpgradeSpringBoot_3_4", "3.3.x", depth = 2),
            )

        val selection = index.find(change.copy(fromVersion = "3.2.0", toVersion = "3.3.5"), Dotted)

        assertEquals(match("UpgradeSpringBoot_3_3", "3.3.x"), selection.chosen)
        assertEquals(emptyList(), selection.alternatives)
    }

    @Test
    fun `the target's depth is the shallowest leaf at the highest selector`() {
        val index =
            index(
                entry("UpgradeSpringCloud", "3.4.x", depth = 3),
                entry("UpgradeSpringCloud", "3.4.x", depth = 2),
                entry("UpgradeSpringCloud", "3.3.x", depth = 1),
            )

        assertEquals(match("UpgradeSpringCloud", "3.4.x", depth = 2), index.find(change, Dotted).chosen)
    }

    @Test
    fun `the tightest selector wins`() {
        val index = index(entry("BestPractices_3", "3.x"), entry("UpgradeSpringBoot_3_4", "3.4.x"), entry("Pin_3_4_1", "3.4.1"))

        val selection = index.find(change, Dotted)

        assertEquals(match("Pin_3_4_1", "3.4.1"), selection.chosen)
        assertEquals(listOf(match("UpgradeSpringBoot_3_4", "3.4.x"), match("BestPractices_3", "3.x")), selection.alternatives)
    }

    @Test
    fun `at the same selector the shallowest leaf wins`() {
        val index = index(entry("UpgradeSpringCloud", "3.4.x", depth = 2), entry("UpgradeSpringBoot_3_4", "3.4.x", depth = 1))

        val selection = index.find(change, Dotted)

        assertEquals(match("UpgradeSpringBoot_3_4", "3.4.x"), selection.chosen)
        assertEquals(listOf(match("UpgradeSpringCloud", "3.4.x", depth = 2)), selection.alternatives)
    }

    @Test
    fun `at the same selector and depth the name decides`() {
        val index = index(entry("b.Upgrade", "3.4.x"), entry("a.Upgrade", "3.4.x"))

        val selection = index.find(change, Dotted)

        assertEquals(match("a.Upgrade", "3.4.x"), selection.chosen)
        assertEquals(listOf(match("b.Upgrade", "3.4.x")), selection.alternatives)
    }

    @Test
    fun `a glob package pattern matches the package`() {
        val index = index(entry("UpgradeSpringBoot_3_4", "3.4.x", pattern = "org.springframework.boot:*"))

        assertEquals(match("UpgradeSpringBoot_3_4", "3.4.x"), index.find(change, Dotted).chosen)
    }

    @Test
    fun `a pattern matches the whole package, not a part, and dots are literal`() {
        val index =
            index(
                entry("Other", "3.4.x", pattern = "org.springframework:*"),
                entry("Dots", "3.4.x", pattern = "org.springframework.boot:spring-boot-starter.parent"),
                entry("Prefix", "3.4.x", pattern = "org.springframework.boot:spring-boot-starter"),
            )

        assertNull(index.find(change.copy(`package` = "org.springframework.boot:spring-boot-starter-parent"), Dotted).chosen)
    }

    @Test
    fun `other packages and other ecosystems don't match`() {
        val index = index(entry("UpgradeSpringBoot_3_4", "3.4.x", pattern = "org.other:lib"))

        assertNull(index.find(change, Dotted).chosen)
        assertNull(index(entry("UpgradeSpringBoot_3_4", "3.4.x")).find(change.copy(ecosystem = "npm"), Dotted).chosen)
    }

    @Test
    fun `an empty index finds nothing`() {
        val selection = index().find(change, Dotted)

        assertNull(selection.chosen)
        assertEquals(emptyList(), selection.alternatives)
    }

    private fun index(vararg entries: IndexEntry) = BundledRecipeIndex(entries.toList())

    private fun entry(
        recipe: String,
        selector: String,
        depth: Int = 1,
        pattern: String = parent,
    ) = IndexEntry("maven", pattern, selector, recipe, depth, "org.openrewrite.recipe:rewrite-spring:6.37.0", "MSAL")

    private fun match(
        recipe: String,
        selector: String,
        depth: Int = 1,
    ) = RecipeMatch(Recipe(recipe, "org.openrewrite.recipe:rewrite-spring:6.37.0", "MSAL"), selector, depth)

    /** Numeric versions; selectors are exact versions or X-ranges. The index must ask this scheme, never compare itself. */
    private object Dotted : VersionScheme {
        override fun compare(
            a: String,
            b: String,
        ) = error("the index never compares versions")

        override fun contains(
            selector: String,
            version: String,
        ) = if (selector.endsWith(".x")) version.startsWith(selector.removeSuffix("x")) else selector == version

        override fun compareSelectors(
            a: String,
            b: String,
        ) = bound(a).compareTo(bound(b))

        override fun updateType(
            from: String,
            to: String,
        ) = error("the index never derives update types")

        // Good enough for single-digit tests: `3.4.x` → `3.4.~` sorts above `3.4.9`, `3.x` above `3.9.~`.
        private fun bound(selector: String) = selector.replace("x", "~")
    }
}
