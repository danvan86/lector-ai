package gmbh.vanella.lector.core

import java.nio.file.Path

/**
 * The extension point for the platform the PR lives on (concept section 2.1), one adapter per host. It is the only part
 * of a run that holds the host's token.
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

    fun label(
        pr: PullRequest,
        label: String,
    )
}
