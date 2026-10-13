package gmbh.vanella.lector.core

import java.nio.file.Path

/**
 * The companion PRs of source PRs (concept 3.1): a branch `heal/<source branch>` with the tool's commits on top of the
 * source PR's head, and one PR from it into the source branch, opened by the tool's bot. The source branch is never
 * touched; only the companion branch is pushed by force.
 */
internal class Companions(
    private val host: GitHost,
) {
    private fun branch(pr: PullRequest) = COMPANION_PREFIX + pr.headBranch

    /**
     * Pushes the commit [repo] is on to [pr]'s companion branch, replacing what it had, and opens the companion PR with
     * [description], or updates the one that is open. Labels it `ai-assisted`. Returns its number.
     */
    fun publish(
        repo: Path,
        pr: PullRequest,
        description: String,
    ): Int {
        val branch = branch(pr)
        host.forcePush(repo, branch)
        val existing = host.findPullRequest(branch, pr.headBranch)
        val number =
            if (existing == null) {
                host.createPullRequest(branch, pr.headBranch, "lector-ai fixes for #${pr.number}", description)
            } else {
                host.updatePullRequest(existing, description)
                existing
            }
        host.label(number, AI_ASSISTED)
        return number
    }

    /** Closes [pr]'s open companion PR and deletes its branch. Returns its number, or null if there is none. */
    fun close(pr: PullRequest): Int? {
        val number = host.findPullRequest(branch(pr), pr.headBranch) ?: return null
        host.closePullRequest(number)
        host.deleteBranch(branch(pr))
        return number
    }

    /**
     * [close] for a source PR that is closed or merged. Also deletes the companion branch when its PR is gone already:
     * merged first, as the setup guide says, or closed by hand.
     */
    fun retire(pr: PullRequest): Int? {
        val number = close(pr)
        if (number == null) host.deleteBranch(branch(pr))
        return number
    }
}
