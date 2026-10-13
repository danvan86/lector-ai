package gmbh.vanella.lector.core

import org.slf4j.LoggerFactory
import java.nio.file.Path

private val logger = LoggerFactory.getLogger(PrPipeline::class.java)

/** The label of every PR that carries the tool's commits. */
const val AI_ASSISTED = "ai-assisted"

/**
 * How a PR run is configured: who commits, the fix loop's limits, the [source] that must take the PR (null: the first
 * that recognizes it), and the [mode] the fixes go in (null: the source's branch policy).
 */
data class PrSettings(
    val identity: CommitIdentity,
    val budgets: Budgets = Budgets(),
    val source: String? = null,
    val mode: BranchPolicy? = null,
)

/**
 * What a run did to a PR the update broke: the recipes, the changes without one and why, the fix loop, the push, the
 * [companion] PR the commits went to, or null if they went onto the PR's own branch or nowhere, and the companion PR of
 * an earlier head that a run without commits [closed][closedCompanion].
 */
data class PrReport(
    val changes: List<DependencyChange>,
    val withoutRecipe: List<Pair<DependencyChange, RecipeOutcome>>,
    val recipes: List<RecipeApplication>,
    val fixLoop: FixLoopResult,
    val pushed: Boolean,
    val companion: Int? = null,
    val closedCompanion: Int? = null,
) {
    /** The verification of the last commit. */
    val verification get() = fixLoop.verification
}

sealed interface PrOutcome {
    /** The run doesn't concern this PR. Nothing happened. */
    data class Skipped(
        val reason: String,
    ) : PrOutcome

    /** Verify found nothing the update caused, so the PR stays as the bot made it. A companion PR is closed. */
    data class Green(
        val verification: Verification,
        val closedCompanion: Int? = null,
    ) : PrOutcome

    /** The PR is closed or merged. Its companion PR, if it had one, is closed. */
    data class Closed(
        val state: PrState,
        val companion: Int?,
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
 * 4. Deliver the commits as the branch policy says: pushed onto the PR's branch, never by force, or onto its companion
 *    branch, which is rebuilt every run, with one companion PR. Then the report comment on the PR, and the
 *    `ai-assisted` label on the PR that carries the commits.
 *
 * A companion PR is closed when its PR is closed or merged, or verify finds the PR green. The working tree must be the
 * PR's head commit, and clean, unless the PR is closed.
 */
class PrPipeline(
    private val host: GitHost,
    private val sources: List<UpdateSource>,
    private val adapters: List<EcosystemAdapter>,
    private val index: RecipeIndex,
    private val llm: LlmClient,
    private val settings: PrSettings,
) {
    private val delivery = PrDelivery(host)

    fun run(repo: Path): PrOutcome {
        val pr = host.pullRequest()
        if (pr.headRepository != pr.baseRepository) return skipped("the PR comes from ${pr.headRepository}, not from ${pr.baseRepository}")
        if (pr.state != PrState.OPEN) return delivery.closed(pr)
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
        val context = RepositoryContext(repo, base, adapter)
        val update =
            try {
                source.read(pr, context)
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
        val policy = settings.mode ?: source.branchPolicy
        return verifyFirst(PrRun(pr, repo, adapter, base, context, changes, update.releaseNotes, policy))
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
        return delivery.green(run.pr, verification)
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
        val recipeStep = applyRecipes(changes, adapter, index, repo, run.context::baseVersions, settings.identity)
        val withoutRecipe = recipeStep.withoutRecipe
        val recipes = recipeStep.applications
        val commits = recipeStep.commits
        val afterRecipes = if (commits.isEmpty()) verification else attribute(adapter, repo, base)
        val fixContext = FixContext(changes, run.releaseNotes, recipeStep.diff(repo))
        val fixLoop = FixLoop(adapter, llm, base, settings.budgets, settings.identity).run(repo, fixContext, afterRecipes)
        val anyCommit = commits.isNotEmpty() || fixLoop.fixes.isNotEmpty()
        return delivery.deliver(pr, repo, PrReport(changes, withoutRecipe, recipes, fixLoop, pushed = anyCommit), run.policy)
    }

    /** One run's state after the trigger checks. */
    private data class PrRun(
        val pr: PullRequest,
        val repo: Path,
        val adapter: EcosystemAdapter,
        val base: BaseCommit,
        val context: SourceContext,
        val changes: List<DependencyChange>,
        val releaseNotes: String?,
        val policy: BranchPolicy,
    )
}
