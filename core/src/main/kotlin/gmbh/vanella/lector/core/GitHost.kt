package gmbh.vanella.lector.core

import java.nio.file.Path

/** The prefix of every companion branch: `heal/<source branch>`. The only branches the tool pushes by force or deletes. */
const val COMPANION_PREFIX = "heal/"

/**
 * The extension point for the platform the PR lives on (concept section 2.1), one adapter per host. It is the only part
 * of a run that holds the host's token. Every PR and branch is in the repository of the run's PR. Nothing here merges or
 * approves (rule 5).
 */
interface GitHost {
    /** The PR the run is for, as the event that started the run shows it. */
    fun pullRequest(): PullRequest

    /** Fetches [commit] of [pr]'s repository into [repo], e.g. a base commit that a shallow clone left out. */
    fun fetch(
        repo: Path,
        pr: PullRequest,
        commit: String,
    )

    /**
     * Pushes the commit [repo] is on to [pr]'s branch, never by force. Returns false if the host rejected it because
     * the branch moved on.
     */
    fun push(
        repo: Path,
        pr: PullRequest,
    ): Boolean

    /** Writes [body] into [pr]'s one report comment: updates it if it exists, creates it otherwise if [create]. */
    fun comment(
        pr: PullRequest,
        body: String,
        create: Boolean,
    )

    /** Adds [label] to PR [number]. */
    fun label(
        number: Int,
        label: String,
    )

    /**
     * Pushes the commit [repo] is on to [branch] by force, replacing whatever it had. Only for a companion branch: fails
     * with a [ToolError] for any branch outside [COMPANION_PREFIX].
     */
    fun forcePush(
        repo: Path,
        branch: String,
    )

    /** The number of the open PR from [head] into [base] that the tool's own bot opened, or null. */
    fun findPullRequest(
        head: String,
        base: String,
    ): Int?

    /** Opens a PR from [head] into [base] and returns its number. */
    fun createPullRequest(
        head: String,
        base: String,
        title: String,
        body: String,
    ): Int

    /** Replaces the description of PR [number] with [body]. */
    fun updatePullRequest(
        number: Int,
        body: String,
    )

    /** Closes PR [number] without merging it. */
    fun closePullRequest(number: Int)

    /**
     * Deletes [branch]. A branch that is already gone is no error. Only for a companion branch: fails with a [ToolError]
     * for any branch outside [COMPANION_PREFIX].
     */
    fun deleteBranch(branch: String)
}
