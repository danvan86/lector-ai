package gmbh.vanella.lector.core

import java.nio.file.Path

/**
 * A recipe the index knows. [artifact] is an opaque string that tells the ecosystem's recipe runner what to fetch
 * (e.g. coordinates with a version). [license] is the license of that artifact.
 */
data class Recipe(
    val name: String,
    val artifact: String,
    val license: String,
)

/** A recipe that matches a change. [selector] is its highest leaf for the package, [depth] how deep that leaf sits. */
data class RecipeMatch(
    val recipe: Recipe,
    val selector: String,
    val depth: Int,
)

/** The best match, if any, and the other matches in rank order. */
data class RecipeSelection(
    val chosen: RecipeMatch?,
    val alternatives: List<RecipeMatch>,
) {
    companion object {
        /** No recipe was looked for. */
        val NONE = RecipeSelection(null, emptyList())
    }
}

/** Finds the recipe for a dependency change. Deterministic: no LLM (rule 2). */
fun interface RecipeIndex {
    fun find(
        change: DependencyChange,
        versions: VersionScheme,
    ): RecipeSelection
}

/** Runs a recipe on a working tree, in place. Optional: not every ecosystem has recipes. */
fun interface RecipeRunner {
    fun run(
        repo: Path,
        recipe: Recipe,
    ): RecipeRun
}

sealed interface RecipeRun {
    data object Applied : RecipeRun

    data class Failed(
        val reason: String,
    ) : RecipeRun
}

/**
 * The effective version of every package in a working tree (`package → version`), including versions that are managed
 * elsewhere (a parent, a lockfile). Fails with a [ToolError] if they can't be read.
 */
fun interface ResolvedVersions {
    fun read(repo: Path): Map<String, String>
}
