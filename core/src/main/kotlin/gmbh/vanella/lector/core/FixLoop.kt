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

/**
 * What every request carries besides its cluster: the [changes] (one, or a group the bot updated together), and the
 * release notes and recipe diff, if any.
 */
data class FixContext(
    val changes: List<DependencyChange>,
    val releaseNotes: String?,
    val recipeDiff: String?,
)

/**
 * One attempt on one cluster: the [diagnostics] it was sent, what the model proposed, and what became of it.
 * [diagnosticsLeftOut] is how many of the cluster's diagnostics didn't fit into the request.
 */
data class Attempt(
    val cluster: String,
    val diagnostics: List<Diagnostic>,
    val edits: List<Edit>,
    val outcome: AttemptOutcome,
    val diagnosticsLeftOut: Int = 0,
)

/**
 * The rejected attempt that got furthest on the run's last commit, as the unified [diff] of its edits: never applied or
 * committed, shown to the reviewer to finish by hand (concept section 8.3). [attempt] is its number in the run, from 1.
 */
data class Suggestion(
    val attempt: Int,
    val diff: String,
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

enum class StopReason(
    val description: String,
) {
    NOTHING_LEFT("nothing caused by the update is left"),
    ATTEMPT_BUDGET("the attempt budget is used up"),
    TIME_BUDGET("the time budget is used up"),
    NO_PROGRESS("no progress in the last attempts"),
    CLUSTER_BUDGETS("every cluster used its attempts"),
    MODEL_UNANSWERED("the model didn't answer in time"),
}

/**
 * Every attempt, why the loop stopped, and the verification of the last commit. A run that ends with failures caused by
 * the update left may have a [suggestion] for the reviewer.
 */
data class FixLoopResult(
    val attempts: List<Attempt>,
    val stopReason: StopReason,
    val verification: Verification,
    val suggestion: Suggestion? = null,
) {
    /** The accepted attempts, in order. */
    val fixes get() = attempts.map { it.outcome }.filterIsInstance<AttemptOutcome.Accepted>()

    /** How many requests left diagnostics out because they didn't fit. */
    val requestsWithDiagnosticsLeftOut get() = attempts.count { it.diagnosticsLeftOut > 0 }

    /** How many attempts each guardrail rule rejected, by rule. */
    fun rejectionsPerRule(): Map<String, Int> =
        attempts
            .map { it.outcome }
            .filterIsInstance<AttemptOutcome.Rejected>()
            .flatMap { rejected -> rejected.violations.map { it.rule }.distinct() }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
}

/**
 * Lets the model fix what the update broke, one cluster at a time (concept section 6). Each attempt is applied, checked
 * by the [Guardrails] and for downgrades if it touched a build file, and verified on its own. It is accepted only if it makes [Progress]: then
 * it becomes one local commit. Otherwise the working tree is reset, and the next request on that cluster gets the
 * attempt and why it failed. The working tree must be clean when the loop starts. Commits are made as [identity].
 */
class FixLoop(
    private val adapter: EcosystemAdapter,
    private val llm: LlmClient,
    private val base: BaseCommit?,
    private val budgets: Budgets = Budgets(),
    private val identity: CommitIdentity = CommitIdentity.TOOL,
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
        private var unanswered = false
        private var versionsBefore: Map<String, String>? = null

        // The lockfiles the adapter updated after the current attempt's build-file edits. They go into its commit.
        private var lockfiles = emptyList<String>()

        // The lines the current attempt's replace texts occupy, per file.
        private var changedLines = emptyMap<String, Set<Int>>()

        // The errors the accepted attempts removed. One that comes back was not uncovered.
        private val removed = mutableSetOf<ErrorKey>()

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
                unanswered -> StopReason.MODEL_UNANSWERED
                attempts.size >= budgets.attempts -> StopReason.ATTEMPT_BUDGET
                timeLeft <= Duration.ZERO -> StopReason.TIME_BUDGET
                withoutProgress >= budgets.attemptsWithoutProgress -> StopReason.NO_PROGRESS
                else -> null
            }

        private fun attemptsOn(key: String) = attempts.filter { it.cluster == key }

        private val timeLeft get() = budgets.time - started.elapsedNow()

        // Whatever ends the run inside an attempt, a build the edits made hang or a parser that doesn't load, must not
        // leave the edits behind. The exception goes on unchanged, unless it is a build stopped by the time budget: that
        // is a rejected attempt, and the loop stops on time with its report.
        @Suppress("TooGenericExceptionCaught")
        private fun attempt(cluster: Cluster) {
            // The first diagnostics that fit into one request, each with its code. The rest come back in a later round:
            // clusters are rebuilt after every accepted attempt (concept section 6.2). Grounding is trimmed to fit, so the
            // checks leave it out: it can take a process per change to read.
            val sent = firstThatFit(cluster.diagnostics) { llm.fits(request(cluster.key, it, grounded = false)) }
            val proposal = llm.propose(request(cluster.key, sent))
            lockfiles = emptyList()
            val outcome =
                try {
                    judge(cluster.key, sent, proposal)
                } catch (e: ProcessTimedOut) {
                    if (timeLeft > Duration.ZERO) {
                        GitWorkingTree.reset(repo)
                        throw e
                    }
                    rejected("the builds ran past the time budget: ${e.message}")
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
                    is Proposal.Invalid, is Proposal.Unanswered -> emptyList()
                }
            // An accepted attempt's edits are in its commit. A rejected attempt's are gone after the reset, and the
            // reason alone doesn't tell a weak model from missing grounding.
            if (outcome is AttemptOutcome.Rejected && edits.isNotEmpty()) {
                logger.info(
                    "The rejected edits:\n{}",
                    edits.joinToString("\n") { "- ${it.file}: ${quoted(it.search)} -> ${quoted(it.replace)}" },
                )
            }
            attempts += Attempt(cluster.key, sent, edits, outcome, cluster.diagnostics.size - sent.size)
        }

        private fun request(
            cluster: String,
            diagnostics: List<Diagnostic>,
            grounded: Boolean = true,
        ): FixRequest {
            val earlier = attemptsOn(cluster)
            val buildFiles = GitWorkingTree.trackedFiles(repo).filter(adapter::isBuildFile)
            return FixRequest(
                changes = context.changes,
                diagnostics = diagnostics,
                snippets = codeSnippets(repo, diagnostics, buildFiles, context.changes.map { it.toVersion }),
                // The API changes of every change come first, so trimming cuts the full APIs before any of them. Changes
                // of one group often ground the same: each text is told once.
                grounding =
                    if (grounded) {
                        (
                            context.changes.map { adapter.apiChanges(repo, it, diagnostics) } +
                                context.changes.map { adapter.ground(repo, it, diagnostics) }
                        ).filter { it.isNotEmpty() }
                            .distinct()
                            .joinToString("\n\n")
                    } else {
                        ""
                    },
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
            cluster: String,
            diagnostics: List<Diagnostic>,
            proposal: Proposal,
        ): AttemptOutcome =
            when (proposal) {
                is Proposal.Invalid -> {
                    rejected("the model's answer is invalid: ${proposal.reason}")
                }

                is Proposal.Unanswered -> {
                    unanswered = true
                    rejected("the model didn't answer: ${proposal.reason}")
                }

                is Proposal.Edits -> {
                    apply(proposal.edits) ?: verify(cluster, diagnostics, proposal)
                }
            }

        /**
         * Writes [edits] unless they break a guardrail or lower a version. Returns the rejection, or null. The scope is
         * checked before anything is read or written: a reset doesn't restore ignored files.
         */
        private fun apply(edits: List<Edit>): AttemptOutcome.Rejected? {
            // A guessed path is a failed edit the model can correct, not a guardrail rejection.
            val failed = if (edits.isEmpty()) "the model proposed no edits" else missingFile(repo, edits, GitWorkingTree.trackedFiles(repo))
            if (failed != null) return rejected(failed)
            val outOfScope = guardrails.checkScope(repo, edits.map { it.file })
            if (outOfScope.isNotEmpty()) return violated(outOfScope)
            val touchesBuildFile = edited(repo, edits).any(adapter::isBuildFile)
            val before = if (touchesBuildFile) versionsBefore().getOrElse { return rejected(unreadable(it)) } else null
            return write(edits) ?: before?.let(::afterBuildFileEdit)
        }

        /** Writes [edits] and checks what they changed with the guardrails. Returns the rejection, or null. */
        private fun write(edits: List<Edit>): AttemptOutcome.Rejected? {
            val files = edited(repo, edits)
            val before = files.associateWith { repo.resolve(it).readText() }
            val applied =
                when (val result = applyEdits(repo, edits)) {
                    is EditResult.Failed -> return rejected(result.reason)
                    is EditResult.Applied -> result
                }
            changedLines = applied.changedLines
            val changes = files.map { FileChange(it, before.getValue(it), repo.resolve(it).readText(), adapter.languageSupport) }
            val violations = guardrails.check(changes, verification.causedByUpdate)
            return if (violations.isEmpty()) null else violated(violations)
        }

        /** After a build-file edit, the lockfiles follow it. Then nothing may resolve lower than [before]. Returns the rejection, or null. */
        private fun afterBuildFileEdit(before: Map<String, String>): AttemptOutcome.Rejected? =
            when (val update = adapter.updateLockfiles(repo, timeLeft)) {
                is LockfileUpdate.Failed -> {
                    rejected("the lockfile can't follow the edits: ${update.reason}")
                }

                is LockfileUpdate.Updated -> {
                    lockfiles = update.files
                    downgradesSince(adapter, repo, before)?.let(::rejected)
                }
            }

        /**
         * Verifies the applied edits. With progress, the files they changed, and the lockfiles that followed them, become a
         * commit with the first line of the rationale as subject. What the build left in the working tree stays out of it.
         */
        private fun verify(
            cluster: String,
            diagnostics: List<Diagnostic>,
            proposal: Proposal.Edits,
        ): AttemptOutcome {
            val after = attribute(adapter, repo, base) { timeLeft }
            val progressBefore = Progress.of(verification.causedByUpdate)
            val progressAfter = Progress.of(after.causedByUpdate)
            val progress =
                progressAfter < progressBefore ||
                    uncovers(diagnostics, verification.causedByUpdate, after.causedByUpdate, changedLines, removed)
            if (!progress) return rejected(noProgress(progressBefore, after.causedByUpdate))
            val subject =
                proposal.rationale
                    .lines()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() } ?: "Fix $cluster"
            val body = "Addresses:\n" + diagnostics.joinToString("\n", transform = ::describe)
            val commit = GitWorkingTree.commit(repo, "$subject\n\n$body", (edited(repo, proposal.edits) + lockfiles).distinct(), identity)
            val left = after.causedByUpdate.map { it.key }.toSet()
            removed += verification.causedByUpdate.map { it.key }.filter { it !in left }
            verification = after
            return AttemptOutcome.Accepted(commit, subject, progressBefore, progressAfter)
        }

        /** The versions before the loop's first build-file edit. The loop commits nothing that lowers them. */
        private fun versionsBefore(): Result<Map<String, String>> =
            versionsBefore?.let { Result.success(it) } ?: readVersions(adapter, repo).onSuccess { versionsBefore = it }
    }
}

/** The longest start of [all] that [fits], and at least its first element. */
private fun <T> firstThatFit(
    all: List<T>,
    fits: (List<T>) -> Boolean,
): List<T> {
    if (fits(all)) return all
    var count = 1
    while (count + 1 < all.size && fits(all.take(count + 1))) count++
    return all.take(count)
}

private fun rejected(reason: String) = AttemptOutcome.Rejected(reason)

/** [text] in quotes on one line, for the log: `"a\n  b"`. */
private fun quoted(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

/** Why the versions of [repo] now are no attempt to accept: some resolve lower than [before]. Null if none does. */
private fun downgradesSince(
    adapter: EcosystemAdapter,
    repo: Path,
    before: Map<String, String>,
): String? {
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
