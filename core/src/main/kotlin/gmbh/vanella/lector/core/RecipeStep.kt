package gmbh.vanella.lector.core

import org.slf4j.LoggerFactory
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("gmbh.vanella.lector.core.RecipeStep")

/** A package whose resolved version differs between two states. Null means the package isn't there. */
data class VersionChange(
    val `package`: String,
    val before: String?,
    val after: String?,
)

/** What the recipe step did. Every outcome but [Applied] leaves the working tree as it was. */
sealed interface RecipeOutcome {
    data class Applied(
        val changedFiles: List<String>,
        val versionChanges: List<VersionChange>,
    ) : RecipeOutcome

    data object NotAnUpgrade : RecipeOutcome

    data class PrOutdated(
        val baseVersion: String,
        val toVersion: String,
    ) : RecipeOutcome

    data object NoMatch : RecipeOutcome

    data object NoRecipeRunner : RecipeOutcome

    data class VersionsUnreadable(
        val reason: String,
    ) : RecipeOutcome

    data class RunFailed(
        val reason: String,
    ) : RecipeOutcome

    data class DowngradeDetected(
        val downgrades: List<VersionChange>,
    ) : RecipeOutcome
}

data class RecipeResult(
    val selection: RecipeSelection,
    val outcome: RecipeOutcome,
)

/**
 * Finds the recipe for [change] and applies it to [repo] in place, unless that could move a dependency back
 * (concept section 4):
 *
 * 1. A change that isn't an upgrade runs no recipe. A new or a removed package isn't one.
 * 2. With a [base]: if the base already resolves the package higher than the change's target, the PR is outdated.
 * 3. If any package resolves lower after the recipe than before, the recipe's result is discarded.
 *
 * Without readable versions none of this can be proven, so no recipe runs. A failed or discarded run resets the
 * working tree, which must be clean before the call. The recipe is optional: no outcome here is an error.
 */
fun applyRecipe(
    change: DependencyChange,
    adapter: EcosystemAdapter,
    index: RecipeIndex,
    repo: Path,
    base: BaseCommit?,
): RecipeResult {
    recipeBlocker(change, adapter, base)?.let { return RecipeResult(RecipeSelection.NONE, it) }
    val selection = index.find(change, adapter.versionScheme)
    logger.info("Recipe for {}: {}, alternatives: {}", change, selection.chosen, selection.alternatives)
    val chosen = selection.chosen
    val outcome = if (chosen == null) RecipeOutcome.NoMatch else runRecipe(chosen.recipe, adapter, repo)
    return RecipeResult(selection, outcome)
}

/** Rules 1 and 2: the outcome that stops any recipe for [change] before one is looked up, or null if none does. */
fun recipeBlocker(
    change: DependencyChange,
    adapter: EcosystemAdapter,
    base: BaseCommit?,
): RecipeOutcome? {
    val baseVersions = base?.let { { it.checkout(resolvedVersions(adapter)::read) } }
    return recipeBlockers(listOf(change), adapter.versionScheme, baseVersions).single()
}

/**
 * Rules 1 and 2 for each of [changes]: the outcome that stops its recipe, or null. Calls [baseVersions] once, and only if
 * a change is an upgrade: a PR can change hundreds of packages. It fails with a [ToolError] if the base's versions can't
 * be read; null means there is no base.
 */
fun recipeBlockers(
    changes: List<DependencyChange>,
    versions: VersionScheme,
    baseVersions: (() -> Map<String, String>)?,
): List<RecipeOutcome?> {
    val upgrades =
        changes.map {
            it.fromVersion.isNotEmpty() && it.toVersion.isNotEmpty() &&
                versions.compare(it.toVersion, it.fromVersion) > 0
        }
    val ruleOne = upgrades.map { if (it) null else RecipeOutcome.NotAnUpgrade }
    if (baseVersions == null || upgrades.none { it }) return ruleOne
    val base =
        try {
            baseVersions()
        } catch (e: ToolError) {
            val unreadable = RecipeOutcome.VersionsUnreadable(e.message)
            return ruleOne.map { it ?: unreadable }
        }
    return changes.zip(ruleOne).map { (change, blocker) -> blocker ?: outdated(change, versions, base) }
}

/** The adapter's resolved versions. Fails with a [ToolError] if its ecosystem can't read them. */
private fun resolvedVersions(adapter: EcosystemAdapter) =
    adapter.resolvedVersions ?: throw ToolError("${adapter.ecosystem} can't read resolved versions")

/** Rule 2: the outcome that stops the recipe, or null if the PR is not outdated. */
private fun outdated(
    change: DependencyChange,
    versions: VersionScheme,
    baseVersions: Map<String, String>,
): RecipeOutcome? {
    val baseVersion = baseVersions[change.`package`] ?: return null
    return if (versions.compare(baseVersion, change.toVersion) > 0) RecipeOutcome.PrOutdated(baseVersion, change.toVersion) else null
}

/** Runs [recipe] on [repo] with rule 3. Every outcome but [RecipeOutcome.Applied] leaves the working tree as it was. */
fun runRecipe(
    recipe: Recipe,
    adapter: EcosystemAdapter,
    repo: Path,
): RecipeOutcome {
    val runner = adapter.recipeRunner ?: return RecipeOutcome.NoRecipeRunner
    val before = readVersions(adapter, repo) { return it }
    val run =
        try {
            runner.run(repo, recipe)
        } catch (e: ToolError) {
            RecipeRun.Failed(e.message)
        }
    val outcome =
        when (run) {
            is RecipeRun.Failed -> RecipeOutcome.RunFailed(run.reason)
            RecipeRun.Applied -> checkVersions(adapter, repo, before)
        }
    if (outcome !is RecipeOutcome.Applied) {
        logger.warn("Recipe {} discarded: {}", recipe.name, outcome)
        GitWorkingTree.reset(repo)
    }
    return outcome
}

/** Rule 3: keeps the recipe's result only if no package went down. */
private fun checkVersions(
    adapter: EcosystemAdapter,
    repo: Path,
    before: Map<String, String>,
): RecipeOutcome {
    val after = readVersions(adapter, repo) { return it }
    val changes = versionChanges(before, after)
    val downgrades = downgrades(changes, adapter.versionScheme)
    if (downgrades.isNotEmpty()) return RecipeOutcome.DowngradeDetected(downgrades)
    return RecipeOutcome.Applied(GitWorkingTree.changedFiles(repo), changes)
}

/** Every package whose version differs between [before] and [after], sorted by package. */
fun versionChanges(
    before: Map<String, String>,
    after: Map<String, String>,
): List<VersionChange> =
    (before.keys + after.keys)
        .sorted()
        .map { VersionChange(it, before[it], after[it]) }
        .filter { it.before != it.after }

/** The changes that move a package that is there before and after to a lower version. */
internal fun downgrades(
    changes: List<VersionChange>,
    versions: VersionScheme,
): List<VersionChange> = changes.filter { it.before != null && it.after != null && versions.compare(it.after, it.before) < 0 }

/** Reads the resolved versions of [repo]. If they can't be read, calls [unreadable], which must leave the caller. */
private inline fun readVersions(
    adapter: EcosystemAdapter,
    repo: Path,
    unreadable: (RecipeOutcome.VersionsUnreadable) -> Nothing,
): Map<String, String> =
    try {
        resolvedVersions(adapter).read(repo)
    } catch (e: ToolError) {
        unreadable(RecipeOutcome.VersionsUnreadable(e.message))
    }

/** Why [change] got no recipe, or why its recipe's result was discarded. Null if it was applied. */
fun RecipeOutcome.reason(change: DependencyChange): String? =
    when (val outcome = this) {
        is RecipeOutcome.Applied -> {
            null
        }

        RecipeOutcome.NotAnUpgrade -> {
            when {
                change.fromVersion.isEmpty() -> "the package is new, so this is not an upgrade"
                change.toVersion.isEmpty() -> "the package is gone, so this is not an upgrade"
                else -> "${change.toVersion} is not higher than ${change.fromVersion}, so this is not an upgrade"
            }
        }

        is RecipeOutcome.PrOutdated -> {
            "PR outdated: the base already resolves ${change.`package`} to ${outcome.baseVersion}, higher than ${outcome.toVersion}"
        }

        RecipeOutcome.NoMatch -> {
            "no recipe matches"
        }

        RecipeOutcome.NoRecipeRunner -> {
            "the ${change.ecosystem} ecosystem has no recipe runner"
        }

        is RecipeOutcome.VersionsUnreadable -> {
            "the resolved versions can't be read, so a downgrade can't be ruled out: ${outcome.reason}"
        }

        is RecipeOutcome.RunFailed -> {
            "the recipe run failed: ${outcome.reason}"
        }

        is RecipeOutcome.DowngradeDetected -> {
            "the recipe moved packages to lower versions, so its result was discarded"
        }
    }
