package gmbh.vanella.lector.recipes

/** A recipe as OpenRewrite describes it, without OpenRewrite's types: its name, its options, and its own recipes. */
data class RecipeTree(
    val name: String,
    val options: Map<String, String?>,
    val children: List<RecipeTree>,
)

/** The artifact that contains a recipe, as coordinates with version, and that artifact's license. */
data class RecipeArtifact(
    val coordinates: String,
    val license: String,
)

data class ScannedRecipe(
    val tree: RecipeTree,
    val artifact: RecipeArtifact,
)

data class GeneratedIndex(
    val entries: List<IndexEntry>,
    val report: IndexReport,
)

// OpenRewrite's leaves that move a Maven package to a new version. Their options name the package and the selector.
private val VERSION_LEAVES =
    setOf(
        "org.openrewrite.maven.UpgradeDependencyVersion",
        "org.openrewrite.java.dependencies.UpgradeDependencyVersion",
        "org.openrewrite.maven.UpgradeParentVersion",
        "org.openrewrite.maven.UpgradePluginVersion",
    )

// The leaf that moves a Gradle plugin, by plugin ID. Its package is the plugin's marker, `<id>:<id>.gradle.plugin`.
private const val GRADLE_PLUGIN_LEAF = "org.openrewrite.gradle.plugins.UpgradePluginVersion"

// Every leaf above is about a package in the Maven namespace, whichever JVM build tool the project uses.
private const val ECOSYSTEM = "maven"
private const val MISSING_OPTION = "missing option"

/**
 * Builds the index from scanned recipes (concept section 4). Every version-upgrade leaf anywhere in a recipe's tree
 * becomes an entry. [skipReason] decides which selectors are supported: null for a supported one, otherwise the reason
 * it is skipped. The build passes OpenRewrite's own selector check, so the index has no selector logic of its own.
 */
class IndexBuilder(
    private val skipReason: (selector: String, versionPattern: String?) -> String?,
) {
    fun build(recipes: List<ScannedRecipe>): GeneratedIndex {
        val skipped = mutableListOf<String>()
        val entries =
            recipes
                .sortedBy { it.tree.name }
                .flatMap { recipe -> entries(recipe, skipped) }
        val indexed = entries.groupBy { it.artifact }.mapValues { (_, it) -> it.distinctBy { entry -> entry.recipe }.size }
        val report =
            IndexReport(
                recipes = entries.distinctBy { it.recipe }.size,
                entries = entries.size,
                skipped = skipped.groupingBy { it }.eachCount(),
                artifacts = recipes.map { it.artifact }.distinct().associateWith { indexed[it.coordinates] ?: 0 },
            )
        return GeneratedIndex(entries, report)
    }

    private fun entries(
        recipe: ScannedRecipe,
        skipped: MutableList<String>,
    ): List<IndexEntry> =
        leaves(recipe.tree.children, depth = 1)
            .mapNotNull { (leaf, depth) -> entry(recipe, leaf, depth, skipped) }
            .sortedBy { it.depth }
            .distinctBy { it.packagePattern to it.selector }

    private fun leaves(
        trees: List<RecipeTree>,
        depth: Int,
    ): List<Pair<RecipeTree, Int>> =
        trees.flatMap { tree ->
            val self = if (tree.name in VERSION_LEAVES || tree.name == GRADLE_PLUGIN_LEAF) listOf(tree to depth) else emptyList()
            self + leaves(tree.children, depth + 1)
        }

    private fun entry(
        recipe: ScannedRecipe,
        leaf: RecipeTree,
        depth: Int,
        skipped: MutableList<String>,
    ): IndexEntry? {
        val pkg = packagePattern(leaf)
        val selector = leaf.options["newVersion"]
        if (pkg == null || selector == null) {
            skipped += MISSING_OPTION
            return null
        }
        val reason = skipReason(selector, leaf.options["versionPattern"])
        if (reason != null) {
            skipped += reason
            return null
        }
        return IndexEntry(
            ECOSYSTEM,
            pkg,
            selector,
            recipe.tree.name,
            depth,
            recipe.artifact.coordinates,
            recipe.artifact.license,
        )
    }

    /** `group:artifact` of a Maven leaf, the marker coordinates of a Gradle plugin leaf, or null if an option is missing. */
    private fun packagePattern(leaf: RecipeTree): String? {
        if (leaf.name == GRADLE_PLUGIN_LEAF) return leaf.options["pluginIdPattern"]?.let { "$it:$it.gradle.plugin" }
        val group = leaf.options["groupId"]
        val artifact = leaf.options["artifactId"]
        return if (group == null || artifact == null) null else "$group:$artifact"
    }
}
