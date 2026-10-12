package gmbh.vanella.lector.core

import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private val logger = LoggerFactory.getLogger(FixLoop::class.java)

// The same prompt at temperature 0 gives the same answer, so a retry raises it a little.
private const val FIRST_TEMPERATURE = 0.0
private const val RETRY_TEMPERATURE = 0.3

// The errors after an attempt without progress that go back to the model. More would crowd out the code.
private const val FEEDBACK_ERRORS = 10

/** The limits of one fix loop (concept section 6.4), and the most lines one attempt may change (the diff-size guardrail). */
data class Budgets(
    val attemptsPerCluster: Int = 3,
    val attempts: Int = 20,
    val time: Duration = 30.minutes,
    val attemptsWithoutProgress: Int = 2,
    val changedLinesPerAttempt: Int = DEFAULT_MAX_CHANGED_LINES,
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

    /**
     * The working tree was reset to the last commit (rule 4: reject, don't repair). [violations] are the guardrails the
     * attempt broke, if that is why.
     */
    data class Rejected(
        val reason: String,
        val violations: List<Violation> = emptyList(),
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
 * by the [Guardrails] and for downgrades if it touched a build file, and verified on its own. It is accepted only if it makes [Progress]: then
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
    private val guardrails = Guardrails(adapter, budgets.changedLinesPerAttempt)

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

        // Whatever ends the run inside an attempt, a build the edits made hang or a parser that doesn't load, must not
        // leave the edits behind. The exception goes on unchanged.
        @Suppress("TooGenericExceptionCaught")
        private fun attempt(cluster: Cluster) {
            val proposal = llm.propose(request(cluster))
            val outcome =
                try {
                    judge(cluster, proposal)
                } catch (e: Throwable) {
                    GitWorkingTree.reset(repo)
                    throw e
                }
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
                is Proposal.Edits -> apply(proposal.edits) ?: verify(cluster, proposal)
            }

        /**
         * Writes [edits] unless they break a guardrail or lower a version. Returns the rejection, or null. The scope is
         * checked before anything is read or written: a reset doesn't restore ignored files.
         */
        private fun apply(edits: List<Edit>): AttemptOutcome.Rejected? {
            if (edits.isEmpty()) return rejected("the model proposed no edits")
            val outOfScope = guardrails.checkScope(repo, edits.map { it.file })
            if (outOfScope.isNotEmpty()) return violated(outOfScope)
            val touchesBuildFile = edited(repo, edits).any(adapter::isBuildFile)
            val before = if (touchesBuildFile) versionsBefore().getOrElse { return rejected(unreadable(it)) } else null
            return write(edits) ?: before?.let(::downgradesSince)?.let(::rejected)
        }

        /** Writes [edits] and checks what they changed with the guardrails. Returns the rejection, or null. */
        private fun write(edits: List<Edit>): AttemptOutcome.Rejected? {
            val files = edited(repo, edits)
            val before = files.associateWith { repo.resolve(it).readText() }
            val failure = applyEdits(repo, edits)
            val violations =
                if (failure == null) {
                    val changes = files.map { FileChange(it, before.getValue(it), repo.resolve(it).readText(), adapter.languageSupport) }
                    guardrails.check(changes, verification.causedByUpdate)
                } else {
                    emptyList()
                }
            return when {
                failure != null -> rejected(failure)
                violations.isNotEmpty() -> violated(violations)
                else -> null
            }
        }

        private fun downgradesSince(before: Map<String, String>): String? {
            val after = readVersions(adapter, repo).getOrElse { return unreadable(it) }
            val downgrades = downgrades(versionChanges(before, after), adapter.versionScheme)
            return if (downgrades.isEmpty()) {
                null
            } else {
                "the edits move packages to lower versions: ${downgrades.joinToString(
                    ", ",
                ) { describe(it) }}"
            }
        }

        /**
         * Verifies the applied edits. With progress, the files they changed become a commit with the first line of the
         * rationale as subject. What the build left in the working tree stays out of it.
         */
        private fun verify(
            cluster: Cluster,
            proposal: Proposal.Edits,
        ): AttemptOutcome {
            val after = attribute(adapter, repo, base)
            val progressBefore = Progress.of(verification.causedByUpdate)
            val progressAfter = Progress.of(after.causedByUpdate)
            if (progressAfter >= progressBefore) return rejected(noProgress(progressBefore, after.causedByUpdate))
            val subject =
                proposal.rationale
                    .lines()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() } ?: "Fix ${cluster.key}"
            val body = "Addresses:\n" + cluster.diagnostics.joinToString("\n", transform = ::describe)
            val commit = GitWorkingTree.commit(repo, "$subject\n\n$body", edited(repo, proposal.edits))
            verification = after
            return AttemptOutcome.Accepted(commit, subject, progressBefore, progressAfter)
        }

        /** The versions before the loop's first build-file edit. The loop commits nothing that lowers them. */
        private fun versionsBefore(): Result<Map<String, String>> =
            versionsBefore?.let { Result.success(it) } ?: readVersions(adapter, repo).onSuccess { versionsBefore = it }
    }
}

private fun rejected(reason: String) = AttemptOutcome.Rejected(reason)

/** The files [edits] change, relative to [repo]'s root. The scope check made sure Git tracks them. */
private fun edited(
    repo: Path,
    edits: List<Edit>,
): List<String> {
    val root = repo.toAbsolutePath().normalize()
    return edits.map { root.relativize(root.resolve(it.file).normalize()).invariantSeparatorsPathString }.distinct()
}

private fun readVersions(
    adapter: EcosystemAdapter,
    repo: Path,
): Result<Map<String, String>> {
    val resolved = adapter.resolvedVersions ?: return Result.failure(ToolError("${adapter.ecosystem} can't read resolved versions"))
    return try {
        Result.success(resolved.read(repo))
    } catch (e: ToolError) {
        Result.failure(e)
    }
}

private fun violated(violations: List<Violation>) =
    AttemptOutcome.Rejected("the edits break the guardrails:\n" + violations.joinToString("\n") { "- $it" }, violations)

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
