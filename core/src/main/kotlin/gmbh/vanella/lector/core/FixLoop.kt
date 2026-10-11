package gmbh.vanella.lector.core

import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private val logger = LoggerFactory.getLogger(FixLoop::class.java)

// The same prompt at temperature 0 gives the same answer, so a retry raises it a little.
private const val FIRST_TEMPERATURE = 0.0
private const val RETRY_TEMPERATURE = 0.3

// The errors after an attempt without progress that go back to the model. More would crowd out the code.
private const val FEEDBACK_ERRORS = 10

/** The limits of one fix loop (concept section 6.4). */
data class Budgets(
    val attemptsPerCluster: Int = 3,
    val attempts: Int = 20,
    val time: Duration = 30.minutes,
    val attemptsWithoutProgress: Int = 2,
)

/** What every request carries besides its cluster: the change, and the release notes and recipe diff, if any. */
data class FixContext(
    val change: DependencyChange,
    val releaseNotes: String?,
    val recipeDiff: String?,
)

/** One attempt on one cluster: what the model proposed, and what became of it. */
data class Attempt(
    val cluster: String,
    val diagnostics: List<Diagnostic>,
    val edits: List<Edit>,
    val outcome: AttemptOutcome,
)

sealed interface AttemptOutcome {
    /** The attempt made progress and became [commit], with [rationale] as its subject. */
    data class Accepted(
        val commit: String,
        val rationale: String,
        val before: Progress,
        val after: Progress,
    ) : AttemptOutcome

    /** The working tree was reset to the last commit (rule 4: reject, don't repair). */
    data class Rejected(
        val reason: String,
    ) : AttemptOutcome
}

enum class StopReason {
    NOTHING_LEFT,
    ATTEMPT_BUDGET,
    TIME_BUDGET,
    NO_PROGRESS,
    CLUSTER_BUDGETS,
}

/** Every attempt, why the loop stopped, and the verification of the last commit. */
data class FixLoopResult(
    val attempts: List<Attempt>,
    val stopReason: StopReason,
    val verification: Verification,
)

/**
 * Lets the model fix what the update broke, one cluster at a time (concept section 6). Each attempt is applied, checked
 * for downgrades if it touched a build file, and verified on its own. It is accepted only if it makes [Progress]: then
 * it becomes one local commit. Otherwise the working tree is reset, and the next request on that cluster gets the
 * attempt and why it failed. The working tree must be clean when the loop starts.
 */
class FixLoop(
    private val adapter: EcosystemAdapter,
    private val llm: LlmClient,
    private val base: BaseCommit?,
    private val budgets: Budgets = Budgets(),
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    /** Runs the loop on [repo], starting from [initial], the attributed verification of its current commit. */
    fun run(
        repo: Path,
        context: FixContext,
        initial: Verification,
    ): FixLoopResult = Session(repo, context, initial).run()

    private inner class Session(
        private val repo: Path,
        private val context: FixContext,
        private var verification: Verification,
    ) {
        private val started = timeSource.markNow()
        private val attempts = mutableListOf<Attempt>()
        private var withoutProgress = 0
        private var versionsBefore: Map<String, String>? = null

        fun run(): FixLoopResult {
            while (true) {
                stopReason()?.let { return FixLoopResult(attempts.toList(), it, verification) }
                val cluster =
                    clusters(verification.causedByUpdate, adapter).firstOrNull { attemptsOn(it.key).size < budgets.attemptsPerCluster }
                        ?: return FixLoopResult(attempts.toList(), StopReason.CLUSTER_BUDGETS, verification)
                attempt(cluster)
            }
        }

        private fun stopReason(): StopReason? =
            when {
                verification.causedByUpdate.isEmpty() -> StopReason.NOTHING_LEFT
                attempts.size >= budgets.attempts -> StopReason.ATTEMPT_BUDGET
                started.elapsedNow() >= budgets.time -> StopReason.TIME_BUDGET
                withoutProgress >= budgets.attemptsWithoutProgress -> StopReason.NO_PROGRESS
                else -> null
            }

        private fun attemptsOn(key: String) = attempts.filter { it.cluster == key }

        private fun attempt(cluster: Cluster) {
            val proposal = llm.propose(request(cluster))
            val outcome = judge(cluster, proposal)
            if (outcome is AttemptOutcome.Accepted) {
                withoutProgress = 0
            } else {
                withoutProgress++
                GitWorkingTree.reset(repo)
            }
            logger.info("Attempt {} on {}: {}", attempts.size + 1, cluster.key, outcome)
            val edits =
                when (proposal) {
                    is Proposal.Edits -> proposal.edits
                    is Proposal.Invalid -> emptyList()
                }
            attempts += Attempt(cluster.key, cluster.diagnostics, edits, outcome)
        }

        private fun request(cluster: Cluster): FixRequest {
            val earlier = attemptsOn(cluster.key)
            val buildFiles = GitWorkingTree.trackedFiles(repo).filter(adapter::isBuildFile)
            return FixRequest(
                change = context.change,
                diagnostics = cluster.diagnostics,
                snippets = codeSnippets(repo, cluster.diagnostics, buildFiles, context.change.toVersion),
                grounding = adapter.ground(repo, context.change, cluster.diagnostics),
                releaseNotes = context.releaseNotes,
                recipeDiff = context.recipeDiff,
                previousAttempts =
                    earlier.mapNotNull { attempt ->
                        (attempt.outcome as? AttemptOutcome.Rejected)?.let { PreviousAttempt(attempt.edits, it.reason) }
                    },
                temperature = if (earlier.isEmpty()) FIRST_TEMPERATURE else RETRY_TEMPERATURE,
            )
        }

        private fun judge(
            cluster: Cluster,
            proposal: Proposal,
        ): AttemptOutcome =
            when (proposal) {
                is Proposal.Invalid -> rejected("the model's answer is invalid: ${proposal.reason}")
                is Proposal.Edits -> apply(proposal.edits)?.let(::rejected) ?: verify(cluster, proposal.rationale)
            }

        /** Applies [edits] unless they can't apply or lower a version. Returns why the attempt is rejected, or null. */
        private fun apply(edits: List<Edit>): String? {
            if (edits.isEmpty()) return "the model proposed no edits"
            val touchesBuildFile = edits.any { adapter.isBuildFile(it.file) }
            val before = if (touchesBuildFile) versionsBefore().getOrElse { return unreadable(it) } else null
            return applyEdits(repo, edits) ?: before?.let(::downgradesSince)
        }

        private fun downgradesSince(before: Map<String, String>): String? {
            val after = readVersions().getOrElse { return unreadable(it) }
            val downgrades = downgrades(versionChanges(before, after), adapter.versionScheme)
            return if (downgrades.isEmpty()) {
                null
            } else {
                "the edits move packages to lower versions: ${downgrades.joinToString(
                    ", ",
                ) { describe(it) }}"
            }
        }

        /** Verifies the applied edits. With progress, they become a commit with the first line of [rationale] as subject. */
        private fun verify(
            cluster: Cluster,
            rationale: String,
        ): AttemptOutcome {
            val after = attribute(adapter, repo, base)
            val progressBefore = Progress.of(verification.causedByUpdate)
            val progressAfter = Progress.of(after.causedByUpdate)
            if (progressAfter >= progressBefore) return rejected(noProgress(progressBefore, after.causedByUpdate))
            val subject = rationale.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "Fix ${cluster.key}"
            val body = "Addresses:\n" + cluster.diagnostics.joinToString("\n", transform = ::describe)
            val commit = GitWorkingTree.commit(repo, "$subject\n\n$body")
            verification = after
            return AttemptOutcome.Accepted(commit, subject, progressBefore, progressAfter)
        }

        /** The versions before the loop's first build-file edit. The loop commits nothing that lowers them. */
        private fun versionsBefore(): Result<Map<String, String>> =
            versionsBefore?.let { Result.success(it) } ?: readVersions().onSuccess { versionsBefore = it }

        private fun readVersions(): Result<Map<String, String>> {
            val resolved = adapter.resolvedVersions ?: return Result.failure(ToolError("${adapter.ecosystem} can't read resolved versions"))
            return try {
                Result.success(resolved.read(repo))
            } catch (e: ToolError) {
                Result.failure(e)
            }
        }
    }
}

private fun rejected(reason: String) = AttemptOutcome.Rejected(reason)

private fun unreadable(error: Throwable) = "the resolved versions can't be read, so a downgrade can't be ruled out: ${error.message}"

private fun describe(change: VersionChange) = "${change.`package`} ${change.before} -> ${change.after}"

/** Why an attempt without progress is rejected, with the errors after it: the model learns what its edits caused. */
private fun noProgress(
    before: Progress,
    after: List<Diagnostic>,
): String {
    val errors = after.take(FEEDBACK_ERRORS).joinToString("\n") { describe(it, wholeMessage = true) }
    val more = if (after.size > FEEDBACK_ERRORS) "\n- and ${after.size - FEEDBACK_ERRORS} more" else ""
    return "no progress: $before -> ${Progress.of(after)}\nErrors after the edits:\n$errors$more"
}

/** `- [javac] App.java:3: message`, with the first line of the message, or the whole message indented. */
private fun describe(
    diagnostic: Diagnostic,
    wholeMessage: Boolean = false,
): String {
    val location = diagnostic.file?.let { listOfNotNull(it, diagnostic.line).joinToString(":") }
    val subject = listOfNotNull(diagnostic.test, location).joinToString(" at ")
    val lines = diagnostic.message.lines()
    val message = if (wholeMessage) lines.joinToString("\n  ") else lines.first()
    return "- [${diagnostic.tool}] " + if (subject.isEmpty()) message else "$subject: $message"
}
