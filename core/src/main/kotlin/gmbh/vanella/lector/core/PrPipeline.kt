package gmbh.vanella.lector.core

import org.slf4j.LoggerFactory
import java.nio.file.Path

private val logger = LoggerFactory.getLogger(PrPipeline::class.java)

/** The label of every PR the tool pushed to. */
const val AI_ASSISTED = "ai-assisted"

/** How a PR run is configured: the bots whose PRs it takes, who commits, and the fix loop's limits. */
data class PrSettings(
    val botLogins: Set<String>,
    val identity: CommitIdentity,
    val budgets: Budgets = Budgets(),
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
 * 1. Trigger checks: a bot login opened the PR from the same repository, on a branch its source recognizes, and the
 *    head commit isn't the tool's own. Otherwise the run is skipped.
 * 2. Verify the PR's head against its base. Green means stop: the PR stays as the bot made it.
 * 3. Run each distinct recipe of the PR's changes once, in the PR's order, one commit each. Then the fix loop.
 * 4. Push the commits, never by force, then the report comment, and the `ai-assisted` label if anything was pushed.
 *
 * The working tree must be the PR's head commit, and clean.
 */
class PrPipeline(
    private val host: GitHost,
    private val source: UpdateSource,
    private val adapters: List<EcosystemAdapter>,
    private val index: RecipeIndex,
    private val llm: LlmClient,
    private val settings: PrSettings,
) {
    fun run(repo: Path): PrOutcome {
        val pr = host.pullRequest()
        val skip = skipReason(pr) ?: ownCommit(pr, repo)
        if (skip != null) return skipped(skip)
        val adapter = adapters.firstOrNull { it.detect(repo) } ?: throw ToolError("No supported ecosystem in $repo")
        val update = source.read(pr)
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
        return verifyFirst(pr, repo, adapter, changes, update.releaseNotes)
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
    private fun verifyFirst(
        pr: PullRequest,
        repo: Path,
        adapter: EcosystemAdapter,
        changes: List<DependencyChange>,
        releaseNotes: String?,
    ): PrOutcome {
        if (!GitWorkingTree.hasCommit(repo, pr.baseSha)) host.fetch(repo, pr, pr.baseSha)
        val base = GitBaseCommit.resolve(repo, pr.baseSha)
        val verification = attribute(adapter, repo, base)
        if (verification.causedByUpdate.isNotEmpty()) return heal(pr, repo, adapter, base, changes, releaseNotes, verification)
        host.comment(pr, PrComment.green(verification, pr.headSha), create = false)
        return PrOutcome.Green(verification)
    }

    private fun skipReason(pr: PullRequest): String? =
        when {
            pr.author !in settings.botLogins -> {
                "the PR's author ${pr.author} is not one of the bot logins ${settings.botLogins.joinToString(", ")}"
            }

            pr.headRepository != pr.baseRepository -> {
                "the PR comes from ${pr.headRepository}, not from ${pr.baseRepository}"
            }

            !source.recognizes(pr) -> {
                "the update source doesn't recognize the branch ${pr.headBranch}"
            }

            else -> {
                null
            }
        }

    private fun skipped(reason: String): PrOutcome {
        logger.info("Skipping the PR: {}", reason)
        return PrOutcome.Skipped(reason)
    }

    /** Recipes, fix loop, push, and report, for a PR the update broke. */
    @Suppress("LongParameterList") // One run's state, passed once from run().
    private fun heal(
        pr: PullRequest,
        repo: Path,
        adapter: EcosystemAdapter,
        base: BaseCommit,
        changes: List<DependencyChange>,
        releaseNotes: String?,
        verification: Verification,
    ): PrOutcome {
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
        val context = FixContext(changes, releaseNotes, recipeDiff)
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

    private sealed interface Lookup {
        data class Found(
            val recipe: Recipe,
        ) : Lookup

        data class Stopped(
            val outcome: RecipeOutcome,
        ) : Lookup
    }
}
