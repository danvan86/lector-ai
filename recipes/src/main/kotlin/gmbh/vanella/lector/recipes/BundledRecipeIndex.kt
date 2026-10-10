package gmbh.vanella.lector.recipes

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Recipe
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.RecipeMatch
import gmbh.vanella.lector.core.RecipeSelection
import gmbh.vanella.lector.core.VersionScheme

/**
 * The recipe index that ships with the tool. Every version question goes to the change's [VersionScheme]: the index
 * itself knows no version syntax.
 *
 * - A recipe's target for a package is its highest leaf for that package, so a recipe that goes beyond the change's
 *   target version never matches.
 * - A recipe matches when its target's selector contains the target version.
 * - Matches are ranked by the tightest selector, then the shallowest leaf, then the recipe name.
 */
class BundledRecipeIndex(
    val entries: List<IndexEntry>,
) : RecipeIndex {
    override fun find(
        change: DependencyChange,
        versions: VersionScheme,
    ): RecipeSelection {
        val bySelector = Comparator<IndexEntry> { a, b -> versions.compareSelectors(a.selector, b.selector) }
        val matches =
            entries
                .filter { it.ecosystem == change.ecosystem && glob(it.packagePattern).matches(change.`package`) }
                .groupBy { it.recipe }
                .values
                .map { leaves -> leaves.sortedWith(bySelector.reversed().thenBy { it.depth }).first() }
                .filter { versions.contains(it.selector, change.toVersion) }
                .sortedWith(bySelector.thenBy { it.depth }.thenBy { it.recipe })
                .map { RecipeMatch(Recipe(it.recipe, it.artifact, it.license), it.selector, it.depth) }
        return RecipeSelection(matches.firstOrNull(), matches.drop(1))
    }

    /** `*` matches any text; everything else is literal. */
    private fun glob(pattern: String) = Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) })

    companion object {
        /** Reads the index from the classpath. Fails if it is missing or malformed: the build always generates it. */
        fun load(resource: String): BundledRecipeIndex {
            val stream =
                checkNotNull(BundledRecipeIndex::class.java.getResourceAsStream(resource)) {
                    "Recipe index $resource is missing from the classpath"
                }
            return BundledRecipeIndex(IndexFormat.read(stream.use { it.readBytes().decodeToString() }))
        }
    }
}
