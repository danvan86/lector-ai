package gmbh.vanella.lector.benchmark

import gmbh.vanella.lector.core.BaseCommit
import gmbh.vanella.lector.core.CommitIdentity
import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.FixContext
import gmbh.vanella.lector.core.FixLoop
import gmbh.vanella.lector.core.FixLoopResult
import gmbh.vanella.lector.core.GitBaseCommit
import gmbh.vanella.lector.core.LlmClient
import gmbh.vanella.lector.core.Progress
import gmbh.vanella.lector.core.RecipeGroupResult
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.RecipeOutcome
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.Verification
import gmbh.vanella.lector.core.applyRecipes
import gmbh.vanella.lector.core.attribute
import gmbh.vanella.lector.core.baseVersions
import gmbh.vanella.lector.core.reason
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.time.TimeSource

private val logger = LoggerFactory.getLogger(Harness::class.java)

/** Gets a fresh working tree of a case's head commit, with its base commit, into a directory. */
fun interface Checkout {
    fun checkout(
        case: Case,
        dir: Path,
    ): Path
}

/** What the benchmark measures: the commit of lector-ai, and the model. Every run records both. */
data class ToolUnderTest(
    val commit: String,
    val model: String,
)

/**
 * Runs one case in one mode with the product's own code and its default budgets, and measures it (spec sections 2 and 3).
 * Every run starts from a fresh checkout and first verifies the head commit against the base: without a failure caused
 * by the update, the case is invalid and the run stops there. A tool error, or any other exception, ends the run as an
 * error.
 */
class Harness(
    private val adapters: List<EcosystemAdapter>,
    private val index: RecipeIndex,
    private val llm: LlmClient,
    private val checkout: Checkout,
    private val tool: ToolUnderTest,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    // A bug of the tool ends the run with a result too. Without one, merge would count it as a job that ran out of time.
    @Suppress("TooGenericExceptionCaught")
    fun run(
        case: Case,
        mode: Mode,
        repetition: Int,
        dir: Path,
    ): RunResult {
        fun result(
            status: RunStatus,
            reason: String?,
            metrics: Metrics?,
        ) = RunResult(case.id, mode, repetition, tool.commit, tool.model, status, reason, metrics, case.ecosystem)
        logger.info("Running {} in mode {}, repetition {}", case.id, mode.id, repetition)
        return try {
            val metrics = Run(case, mode, checkout.checkout(case, dir)).measure()
            if (metrics == null) result(RunStatus.INVALID, NOT_BROKEN, null) else result(RunStatus.VALID, null, metrics)
        } catch (e: ToolError) {
            logger.error("Run of {} in mode {} failed", case.id, mode.id, e)
            result(RunStatus.ERROR, e.message, null)
        } catch (e: Exception) {
            logger.error("Run of {} in mode {} failed", case.id, mode.id, e)
            result(RunStatus.ERROR, "${e::class.simpleName}: ${e.message}", null)
        }
    }

    private inner class Run(
        case: Case,
        private val mode: Mode,
        private val repo: Path,
    ) {
        private val started = timeSource.markNow()
        private val adapter =
            adapters.firstOrNull { it.detect(repo) }?.also { detected ->
                if (detected.ecosystem != case.ecosystem) {
                    throw ToolError(
                        "${case.id} is a ${case.ecosystem} case in the corpus, but its repository is a ${detected.ecosystem} build",
                    )
                }
            } ?: throw ToolError("No supported ecosystem in ${case.repository}")
        private val base: BaseCommit = GitBaseCommit.resolve(repo, case.base)
        private val changes =
            case.changes.map {
                DependencyChange(adapter.ecosystem, it.`package`, it.from, it.to, adapter.versionScheme.updateType(it.from, it.to))
            }

        /** The metrics of the run, or null if the head commit has no failure caused by the update. */
        fun measure(): Metrics? {
            val (before, verifyTime) = timed { attribute(adapter, repo, base) }
            if (before.causedByUpdate.isEmpty()) return null
            val (step, recipeTime) =
                timed {
                    if (mode.usesRecipe) {
                        applyRecipes(
                            changes,
                            adapter,
                            index,
                            repo,
                            baseVersions(adapter, base),
                            CommitIdentity.TOOL,
                        )
                    } else {
                        null
                    }
                }
            val recipeCommits = if (step == null) emptyList() else step.commits
            // Nothing to verify again if no recipe changed anything.
            val (afterRecipes, verifyAgainTime) = timed { if (recipeCommits.isEmpty()) before else attribute(adapter, repo, base) }
            val (loop, loopTime) = timed { if (mode.usesFixLoop) fixLoop(step, afterRecipes) else null }
            val after = if (loop == null) afterRecipes else loop.verification
            val fixes = if (loop == null) LoopNumbers.NONE else LoopNumbers.of(loop)
            return Metrics(
                green = after.causedByUpdate.isEmpty() && (after.testsRun ?: 0) > 0,
                before = Stages.of(Progress.of(before.causedByUpdate)),
                after = Stages.of(Progress.of(after.causedByUpdate)),
                recipes = if (step == null) emptyList() else records(step),
                attempts = fixes.attempts,
                acceptedFixes = fixes.commits.size,
                rejectionsPerRule = fixes.rejectionsPerRule,
                stopReason = fixes.stopReason,
                runtime = Runtime(started.elapsedNow().inWholeSeconds, verifyTime, recipeTime + verifyAgainTime, loopTime),
                recipeLines = recipeCommits.sumOf { changedLines(repo, it) },
                fixLines = fixes.commits.sumOf { changedLines(repo, it) },
                preExisting = after.preExisting.size,
                flaky = after.flaky.size,
                unattributed = after.unattributed.size,
                testsRun = after.testsRun,
                requestsWithDiagnosticsLeftOut = fixes.requestsWithDiagnosticsLeftOut,
            )
        }

        private fun fixLoop(
            step: RecipeGroupResult?,
            initial: Verification,
        ): FixLoopResult {
            val recipeDiff = if (step == null) null else step.diff(repo)
            // No release notes: the product reads none from Dependabot PRs before M8, and the report says so (review F4).
            return FixLoop(adapter, llm, base).run(repo, FixContext(changes, null, recipeDiff), initial)
        }

        private fun <T> timed(block: () -> T): Pair<T, Long> {
            val mark = timeSource.markNow()
            val value = block()
            return value to mark.elapsedNow().inWholeSeconds
        }
    }

    private companion object {
        const val NOT_BROKEN = "the head commit has no failure caused by the update"
    }
}

/** What a run's fix loop did, or nothing if the mode has none. */
private data class LoopNumbers(
    val attempts: Int,
    val commits: List<String>,
    val rejectionsPerRule: Map<String, Int>,
    val stopReason: String?,
    val requestsWithDiagnosticsLeftOut: Int,
) {
    companion object {
        val NONE = LoopNumbers(0, emptyList(), emptyMap(), null, 0)

        fun of(loop: FixLoopResult) =
            LoopNumbers(
                loop.attempts.size,
                loop.fixes.map { it.commit },
                loop.rejectionsPerRule(),
                loop.stopReason.name.lowercase(),
                loop.requestsWithDiagnosticsLeftOut,
            )
    }
}

/** One record per change: the recipe found for it, if any, and what became of it. */
private fun records(step: RecipeGroupResult): List<RecipeRecord> =
    step.withoutRecipe.map { (change, outcome) -> RecipeRecord(describe(change), null, false, outcome(change, outcome, null)) } +
        step.applications.flatMap { application ->
            application.changes.map { change ->
                RecipeRecord(
                    describe(change),
                    application.recipe.name,
                    application.commit != null,
                    outcome(change, application.outcome, application.commit),
                )
            }
        }

private fun describe(change: DependencyChange) = "${change.`package`} ${change.fromVersion} -> ${change.toVersion}"

private fun outcome(
    change: DependencyChange,
    outcome: RecipeOutcome,
    commit: String?,
) = outcome.reason(change) ?: if (commit != null) "applied" else "applied, but changed nothing"
