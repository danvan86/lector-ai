package gmbh.vanella.lector.core

import java.nio.file.Path

/** A recipe that ran once for the [changes] that map to it, and its commit if it changed anything. */
data class RecipeApplication(
    val recipe: Recipe,
    val changes: List<DependencyChange>,
    val outcome: RecipeOutcome,
    val commit: String?,
)

/** What the recipe step did for a group of changes: the changes without a recipe and why, and the recipes that ran. */
data class RecipeGroupResult(
    val withoutRecipe: List<Pair<DependencyChange, RecipeOutcome>>,
    val applications: List<RecipeApplication>,
) {
    /** The commits of the recipes that changed anything, in order. */
    val commits get() = applications.mapNotNull { it.commit }

    /** What the recipes changed in [repo], as one unified diff, or null if they changed nothing. */
    fun diff(repo: Path) = commits.joinToString("\n") { GitWorkingTree.diff(repo, it) }.ifEmpty { null }
}

/**
 * The recipe step for the [changes] of one update (concept section 4): rules 1 and 2 for each change, then each distinct
 * recipe once, in the changes' order, with rule 3. A recipe that changed anything becomes one commit by [identity]. The
 * working tree must be clean.
 */
@Suppress("LongParameterList") // The inputs of applyRecipe, for a group of changes, and who commits.
fun applyRecipes(
    changes: List<DependencyChange>,
    adapter: EcosystemAdapter,
    index: RecipeIndex,
    repo: Path,
    base: BaseCommit,
    identity: CommitIdentity,
): RecipeGroupResult {
    val lookups = changes.map { it to lookUp(it, adapter, index, base) }
    val withoutRecipe = lookups.mapNotNull { (change, lookup) -> (lookup as? Lookup.Stopped)?.let { change to it.outcome } }
    val applications =
        lookups
            .mapNotNull { (change, lookup) -> (lookup as? Lookup.Found)?.let { it.recipe to change } }
            .groupBy({ it.first }, { it.second })
            .map { (recipe, covered) -> apply(recipe, covered, adapter, repo, identity) }
    return RecipeGroupResult(withoutRecipe, applications)
}

/** Rules 1 and 2 of the recipe step, then the index. */
private fun lookUp(
    change: DependencyChange,
    adapter: EcosystemAdapter,
    index: RecipeIndex,
    base: BaseCommit,
): Lookup {
    val blocker = recipeBlocker(change, adapter, base)
    if (blocker != null) return Lookup.Stopped(blocker)
    val chosen = index.find(change, adapter.versionScheme).chosen
    return if (chosen == null) Lookup.Stopped(RecipeOutcome.NoMatch) else Lookup.Found(chosen.recipe)
}

/** Runs [recipe] once for every change in [changes], and commits what it changed. */
private fun apply(
    recipe: Recipe,
    changes: List<DependencyChange>,
    adapter: EcosystemAdapter,
    repo: Path,
    identity: CommitIdentity,
): RecipeApplication {
    val outcome = runRecipe(recipe, adapter, repo)
    val changed = (outcome as? RecipeOutcome.Applied)?.changedFiles.orEmpty()
    val commit =
        if (changed.isEmpty()) {
            null
        } else {
            val versions = changes.joinToString(", ") { "${it.`package`} ${it.fromVersion} -> ${it.toVersion}" }
            GitWorkingTree.commit(repo, "Apply recipe ${recipe.name}\n\nFor $versions.", changed, identity)
        }
    return RecipeApplication(recipe, changes, outcome, commit)
}

private sealed interface Lookup {
    data class Found(
        val recipe: Recipe,
    ) : Lookup

    data class Stopped(
        val outcome: RecipeOutcome,
    ) : Lookup
}
