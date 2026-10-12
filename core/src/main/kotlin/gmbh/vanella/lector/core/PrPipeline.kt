package gmbh.vanella.lector.core

import org.slf4j.LoggerFactory
import java.nio.file.Path

private val logger = LoggerFactory.getLogger(PrPipeline::class.java)

/** The label of every PR the tool pushed to. */
const val AI_ASSISTED = "ai-assisted"

/**
 * How a PR run is configured: who commits, the fix loop's limits, and the [source] that must take the PR, or null to let
 * the first source that recognizes it take it.
 */
data class PrSettings(
    val identity: CommitIdentity,
    val budgets: Budgets = Budgets(),
    val source: String? = null,
)

/** A recipe that ran once for the [changes] that map to it, and its commit if it changed anything. */
data class RecipeApplication(
    val recipe: Recipe,
    val changes: List<DependencyChange>,
    val outcome: RecipeOutcome,
    val commit: String?,
)

/** What a run did to a PR the update broke: the recipes, the changes without one and why, the fix loop, the push. */
data class PrReport(
    val changes: List<DependencyChange>,
    val withoutRecipe: List<Pair<DependencyChange, RecipeOutcome>>,
    val recipes: List<RecipeApplication>,
    val fixLoop: FixLoopResult,
    val pushed: Boolean,
) {
    /** The verification of the last commit. */
    val verification get() = fixLoop.verification
}

sealed interface PrOutcome {
    /** The run doesn't concern this PR. Nothing happened. */
    data class Skipped(
        val reason: String,
    ) : PrOutcome

    /** Verify found nothing the update caused, so the PR stays as the bot made it. */
    data class Green(
        val verification: Verification,
    ) : PrOutcome

    /** The branch moved on while the run worked. Nothing was pushed or reported: the new head gets its own run. */
    data class PushRejected(
        val report: PrReport,
    ) : PrOutcome

    /** The run worked on the PR, pushed what it could, and reported it. */
    data class Reported(
        val report: PrReport,
    ) : PrOutcome
}

/**
 * The pipeline for one dependency PR (concept section 8), the same for every source, ecosystem, and host:
 *
 * 1. Trigger checks: the PR comes from the same repository, one of the [sources] recognizes it (the first in the list,
 *    or the one the settings name), and the head commit isn't the tool's own. Otherwise the run is skipped.
 * 2. Verify the PR's head against its base. Green means stop: the PR stays as the bot made it.
 * 3. Run each distinct recipe of the PR's changes once, in the PR's order, one commit each. Then the fix loop.
 * 4. Push the commits, never by force, then the report comment, and the `ai-assisted` label if anything was pushed.
 *
 * The working tree must be the PR's head commit, and clean.
 */
class PrPipeline(
    private val host: GitHost,
    private val sources: List<UpdateSource>,
    private val adapters: List<EcosystemAdapter>,
    private val index: RecipeIndex,
    private val llm: LlmClient,
    private val settings: PrSettings,
) {
    fun run(repo: Path): PrOutcome {
        val pr = host.pullRequest()
        if (pr.headRepository != pr.baseRepository) return skipped("the PR comes from ${pr.headRepository}, not from ${pr.baseRepository}")
        val source = source(pr) ?: return skipped(unrecognized(pr))
        val ownCommit = ownCommit(pr, repo)
        return if (ownCommit == null) read(pr, source, repo) else skipped(ownCommit)
    }

    /** The source that takes [pr]: the first that recognizes it, or the one the settings name if it does. */
    private fun source(pr: PullRequest): UpdateSource? {
        val name = settings.source ?: return sources.firstOrNull { it.recognizes(pr) }
        val named = sources.firstOrNull { it.name == name }
        if (named == null) throw ToolError("No update source is called $name. The sources are ${sources.joinToString(", ") { it.name }}.")
        return if (named.recognizes(pr)) named else null
    }

    private fun unrecognized(pr: PullRequest): String {
        val by = "the PR by ${pr.author} on ${pr.headBranch}"
        val name = settings.source
        return if (name == null) "no update source recognizes $by" else "the $name source doesn't recognize $by"
    }

    /** Reads the PR's changes with [source], then verifies. */
    private fun read(
        pr: PullRequest,
        source: UpdateSource,
        repo: Path,
    ): PrOutcome {
        val adapter = adapters.firstOrNull { it.detect(repo) } ?: throw ToolError("No supported ecosystem in $repo")
        if (!GitWorkingTree.hasCommit(repo, pr.baseSha)) host.fetch(repo, pr, pr.baseSha)
        val base = GitBaseCommit.resolve(repo, pr.baseSha)
        val update =
            try {
                source.read(pr, RepositoryContext(repo, base, adapter))
            } catch (e: ToolError) {
                host.comment(pr, PrComment.unreadable(e.message), create = true)
                throw e
            }
        val changes =
            update.updates
                .filter { it.ecosystem == null || it.ecosystem == adapter.ecosystem }
                .map {
                    DependencyChange(
                        adapter.ecosystem,
                        it.`package`,
                        it.fromVersion,
                        it.toVersion,
                        adapter.versionScheme.updateType(it.fromVersion, it.toVersion),
                    )
                }
        if (changes.isEmpty()) return skipped("the PR changes no version of the repository's ${adapter.ecosystem} packages")
        return verifyFirst(PrRun(pr, repo, adapter, base, changes, update.releaseNotes))
    }

    /** The last trigger check, on the working tree, which must be the PR's head: whether that commit is the tool's own. */
    private fun ownCommit(
        pr: PullRequest,
        repo: Path,
    ): String? {
        GitWorkingTree.requireClean(repo)
        val head = GitWorkingTree.head(repo)
        if (head != pr.headSha) {
            throw ToolError("The working tree is on $head, not on the PR's head ${pr.headSha}. Check out the PR's head commit.")
        }
        return if (GitWorkingTree.authorEmail(repo, head) == settings.identity.email) "the head commit is the tool's own" else null
    }

    /** Verifies the PR's head against its base. Green means stop, broken means heal. */
    private fun verifyFirst(run: PrRun): PrOutcome {
        val verification = attribute(run.adapter, run.repo, run.base)
        if (verification.causedByUpdate.isNotEmpty()) return heal(run, verification)
        host.comment(run.pr, PrComment.green(verification, run.pr.headSha), create = false)
        return PrOutcome.Green(verification)
    }

    private fun skipped(reason: String): PrOutcome {
        logger.info("Skipping the PR: {}", reason)
        return PrOutcome.Skipped(reason)
    }

    /** Recipes, fix loop, push, and report, for a PR the update broke. */
    private fun heal(
        run: PrRun,
        verification: Verification,
    ): PrOutcome {
        val (pr, repo, adapter) = run
        val base = run.base
        val changes = run.changes
        val lookups = changes.map { it to lookUp(it, adapter, base) }
        val withoutRecipe = lookups.mapNotNull { (change, lookup) -> (lookup as? Lookup.Stopped)?.let { change to it.outcome } }
        val recipes =
            lookups
                .mapNotNull { (change, lookup) -> (lookup as? Lookup.Found)?.let { it.recipe to change } }
                .groupBy({ it.first }, { it.second })
                .map { (recipe, covered) -> apply(recipe, covered, adapter, repo) }
        val commits = recipes.mapNotNull { it.commit }
        val afterRecipes = if (commits.isEmpty()) verification else attribute(adapter, repo, base)
        val recipeDiff = commits.joinToString("\n") { GitWorkingTree.diff(repo, it) }.ifEmpty { null }
        val context = FixContext(changes, run.releaseNotes, recipeDiff)
        val fixLoop = FixLoop(adapter, llm, base, settings.budgets, settings.identity).run(repo, context, afterRecipes)
        val anyCommit = commits.isNotEmpty() || fixLoop.fixes.isNotEmpty()
        if (anyCommit && !host.push(repo, pr)) {
            logger.warn("The push was rejected: the branch moved on. The new head gets its own run.")
            return PrOutcome.PushRejected(PrReport(changes, withoutRecipe, recipes, fixLoop, pushed = false))
        }
        val report = PrReport(changes, withoutRecipe, recipes, fixLoop, pushed = anyCommit)
        host.comment(pr, PrComment.render(report), create = true)
        if (anyCommit) host.label(pr, AI_ASSISTED)
        return PrOutcome.Reported(report)
    }

    /** Rules 1 and 2 of the recipe step, then the index. */
    private fun lookUp(
        change: DependencyChange,
        adapter: EcosystemAdapter,
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
    ): RecipeApplication {
        val outcome = runRecipe(recipe, adapter, repo)
        val changed = (outcome as? RecipeOutcome.Applied)?.changedFiles.orEmpty()
        val commit =
            if (changed.isEmpty()) {
                null
            } else {
                val versions = changes.joinToString(", ") { "${it.`package`} ${it.fromVersion} -> ${it.toVersion}" }
                GitWorkingTree.commit(repo, "Apply recipe ${recipe.name}\n\nFor $versions.", changed, settings.identity)
            }
        return RecipeApplication(recipe, changes, outcome, commit)
    }

    /** One run's state after the trigger checks. */
    private data class PrRun(
        val pr: PullRequest,
        val repo: Path,
        val adapter: EcosystemAdapter,
        val base: BaseCommit,
        val changes: List<DependencyChange>,
        val releaseNotes: String?,
    )

    private sealed interface Lookup {
        data class Found(
            val recipe: Recipe,
        ) : Lookup

        data class Stopped(
            val outcome: RecipeOutcome,
        ) : Lookup
    }
}
