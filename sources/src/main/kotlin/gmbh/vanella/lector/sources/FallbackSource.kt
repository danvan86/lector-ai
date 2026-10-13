package gmbh.vanella.lector.sources

import gmbh.vanella.lector.core.BranchPolicy
import gmbh.vanella.lector.core.PackageUpdate
import gmbh.vanella.lector.core.PullRequest
import gmbh.vanella.lector.core.SourceContext
import gmbh.vanella.lector.core.SourceUpdate
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.UpdateSource
import gmbh.vanella.lector.core.versionChanges

/**
 * Any other dependency PR (concept section 3): manual bumps, Snyk, future bots. Recognized by the opt-in [label] only,
 * since anyone may open a PR. The changes are every package whose resolved version differs between the PR's base and
 * head, transitive ones included: resolved versions also catch changes made through properties, BOM imports, parents,
 * and plugins. Without readable versions the run can't tell what changed, so it stops. No release notes. The fixes go
 * into a companion PR.
 */
class FallbackSource(
    private val label: String,
) : UpdateSource {
    override val name = "fallback"
    override val branchPolicy = BranchPolicy.COMPANION

    override fun recognizes(pr: PullRequest) = label in pr.labels

    override fun read(
        pr: PullRequest,
        context: SourceContext,
    ): SourceUpdate {
        val before = versions("the base commit", context::baseVersions)
        val after = versions("the PR's head", context::headVersions)
        val updates =
            versionChanges(
                before,
                after,
            ).map { PackageUpdate(context.ecosystem, it.`package`, it.before.orEmpty(), it.after.orEmpty()) }
        return SourceUpdate(updates, releaseNotes = null)
    }

    private fun versions(
        side: String,
        read: () -> Map<String, String>,
    ): Map<String, String> =
        try {
            read()
        } catch (e: ToolError) {
            throw ToolError("The resolved versions of $side can't be read, so lector-ai can't tell what the PR changes: ${e.message}", e)
        }
}
