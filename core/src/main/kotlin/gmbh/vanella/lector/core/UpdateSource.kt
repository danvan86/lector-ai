package gmbh.vanella.lector.core

/** Whether a PR is still open, and how it ended. */
enum class PrState {
    OPEN,
    CLOSED,
    MERGED,
}

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
    val labels: Set<String> = emptySet(),
    val state: PrState = PrState.OPEN,
)

/** Where the fixes go: commits on the PR's own branch, or a stacked companion PR from `heal/<branch>` (concept 3.1). */
enum class BranchPolicy {
    IN_PLACE,
    COMPANION,
}

/**
 * One version change a PR names. [ecosystem] is the package namespace, or null if the PR doesn't say: then it's the
 * repository's. An empty version means the package isn't there on that side. The update type is left to the
 * ecosystem's version scheme, which the source doesn't know.
 */
data class PackageUpdate(
    val ecosystem: String?,
    val `package`: String,
    val fromVersion: String,
    val toVersion: String,
)

/** What a source reads from a PR: every version change in the order the PR lists them, and the release notes. */
data class SourceUpdate(
    val updates: List<PackageUpdate>,
    val releaseNotes: String?,
)

/**
 * What a source may look at besides the PR: the repository's ecosystem and the resolved versions of the PR's base and
 * head. Reading versions runs the build tool, so a source that doesn't need them doesn't ask.
 */
interface SourceContext {
    /** The package namespace of the repository, e.g. `maven`. */
    val ecosystem: String

    /** The resolved versions of the PR's base commit. Fails with a [ToolError] if they can't be read. */
    fun baseVersions(): Map<String, String>

    /** The resolved versions of the PR's head. Fails with a [ToolError] if they can't be read. */
    fun headVersions(): Map<String, String>
}

/** The extension point for whoever opens dependency PRs (concept section 3), one adapter per bot, plus the fallback. */
interface UpdateSource {
    /** The name the `source` option selects this source by, e.g. `renovate`. */
    val name: String

    /** Where the fixes go unless the run's mode says otherwise. */
    val branchPolicy: BranchPolicy

    /** Whether this source takes [pr]: its own trigger checks, e.g. the author and the branch prefix, or a label. */
    fun recognizes(pr: PullRequest): Boolean

    /**
     * The version changes and release notes of [pr]. Rows without a version change (digests, pins, lock files) are left
     * out. Fails with a [ToolError] if the changes can't be read: the run can't tell what the PR changed.
     */
    fun read(
        pr: PullRequest,
        context: SourceContext,
    ): SourceUpdate
}
