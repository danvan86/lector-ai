package gmbh.vanella.lector.core

import org.slf4j.LoggerFactory
import java.nio.file.Path

private val logger = LoggerFactory.getLogger(PrDelivery::class.java)

/**
 * The end of a PR run on the host: the commits pushed as the branch policy says, the report comment on the PR, the
 * `ai-assisted` label on the PR that carries the commits, and the companion PR closed once it has no reason left: its PR
 * is closed or green, or a run on a new head has no commits for it.
 */
internal class PrDelivery(
    private val host: GitHost,
) {
    private val companions = Companions(host)

    /**
     * Delivers the commits of [report] as [policy] says, then the comment. Without commits, a companion PR is closed: it
     * is on an earlier head, and merging it would bring that head back.
     */
    fun deliver(
        pr: PullRequest,
        repo: Path,
        report: PrReport,
        policy: BranchPolicy,
    ): PrOutcome =
        when {
            report.pushed && policy == BranchPolicy.IN_PLACE -> inPlace(pr, repo, report)
            report.pushed -> companion(pr, repo, report)
            policy == BranchPolicy.COMPANION -> reported(pr, report.copy(closedCompanion = companions.close(pr)))
            else -> reported(pr, report)
        }

    /** Closes the companion PR of a green PR, and updates the comment. */
    fun green(
        pr: PullRequest,
        verification: Verification,
    ): PrOutcome {
        val companion = companions.close(pr)
        val closed = if (companion == null) null else PrComment.companionClosed(companion, "verify is green without it")
        host.comment(pr, PrComment.green(verification, pr.headSha, closed), create = false)
        return PrOutcome.Green(verification, companion)
    }

    /** Closes the companion PR of a PR that is closed or merged, deletes its branch, and says why in the comment. */
    fun closed(pr: PullRequest): PrOutcome {
        val companion = companions.retire(pr)
        if (companion != null) {
            val reason = if (pr.state == PrState.MERGED) "this PR was merged" else "this PR was closed"
            host.comment(pr, PrComment.note(PrComment.companionClosed(companion, reason)), create = false)
        }
        logger.info("The PR is closed. Companion PR closed: {}", companion)
        return PrOutcome.Closed(pr.state, companion)
    }

    /** Pushes the commits onto the PR's branch, never by force, and labels the PR. Nothing else if the branch moved on. */
    private fun inPlace(
        pr: PullRequest,
        repo: Path,
        report: PrReport,
    ): PrOutcome {
        if (!host.push(repo, pr)) {
            logger.warn("The push was rejected: the branch moved on. The new head gets its own run.")
            return PrOutcome.PushRejected(report.copy(pushed = false))
        }
        val outcome = reported(pr, report)
        host.label(pr.number, AI_ASSISTED)
        return outcome
    }

    /** Puts the commits onto the PR's companion branch, with its companion PR. */
    private fun companion(
        pr: PullRequest,
        repo: Path,
        report: PrReport,
    ): PrOutcome {
        val number = companions.publish(repo, pr, PrComment.companionDescription(report, pr))
        return reported(pr, report.copy(companion = number))
    }

    private fun reported(
        pr: PullRequest,
        report: PrReport,
    ): PrOutcome {
        host.comment(pr, PrComment.render(report), create = true)
        return PrOutcome.Reported(report)
    }
}
