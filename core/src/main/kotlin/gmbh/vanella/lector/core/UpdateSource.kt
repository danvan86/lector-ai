package gmbh.vanella.lector.core

/** A pull request as the Git host's event shows it. Repositories are `owner/name`. */
data class PullRequest(
    val number: Int,
    val author: String,
    val headBranch: String,
    val headSha: String,
    val headRepository: String,
    val baseRepository: String,
    val baseSha: String,
    val body: String,
)

/** Where the fixes go. Only in-place commits on the PR's own branch exist so far; companion PRs come with M8. */
enum class BranchPolicy {
    IN_PLACE,
}

/**
 * One version change a PR names. [ecosystem] is the package namespace, or null if the PR doesn't say: then it's the
 * repository's. The update type is left to the ecosystem's version scheme, which the source doesn't know.
 */
data class PackageUpdate(
    val ecosystem: String?,
    val `package`: String,
    val fromVersion: String,
    val toVersion: String,
)

/** What a source reads from a PR: every version change in the order the PR lists them, the release notes, the branch policy. */
data class SourceUpdate(
    val updates: List<PackageUpdate>,
    val releaseNotes: String?,
    val branchPolicy: BranchPolicy,
)

/** The extension point for the bots that open dependency PRs (concept section 3), one adapter per bot. */
interface UpdateSource {
    /** Whether this source's bot made [pr], judged by what only the PR shows, e.g. its branch prefix. */
    fun recognizes(pr: PullRequest): Boolean

    /** The version changes and release notes of [pr]. Rows without a version change (digests, pins, lock files) are left out. */
    fun read(pr: PullRequest): SourceUpdate
}
