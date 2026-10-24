package gmbh.vanella.lector.sources

import gmbh.vanella.lector.core.BranchPolicy
import gmbh.vanella.lector.core.CommitIdentity
import gmbh.vanella.lector.core.PackageUpdate
import gmbh.vanella.lector.core.PullRequest
import gmbh.vanella.lector.core.SourceContext
import gmbh.vanella.lector.core.SourceUpdate
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.UpdateSource
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DependabotSource::class.java)

private const val BRANCH_PREFIX = "dependabot/"

// Dependabot's package ecosystems (the second part of its branch names) and the ecosystem each one is.
private val ECOSYSTEMS =
    mapOf(
        "maven" to "maven",
        "gradle" to "maven",
        // fetch-metadata reports `npm_and_yarn` for npm, pnpm, and Yarn alike.
        "npm_and_yarn" to "npm",
        "npm" to "npm",
    )

// Gradle's wrapper: Dependabot updates it like a dependency, but it is the build tool, no package.
private const val GRADLE_WRAPPER = "gradle-wrapper"

// The collapsible sections of Dependabot's body that are release notes; the others are commit lists and commands.
private val NOTES = Regex("""<details>\s*<summary>(?:Release notes|Changelog)</summary>(.*?)</details>""", RegexOption.DOT_MATCHES_ALL)

// The line that names the update a section belongs to: `Bumps …` for one update, `Updates `…` from …` in a group.
private val HEADING = Regex("""^(?:Bumps |Updates `).*$""", RegexOption.MULTILINE)

/** One entry of `dependabot/fetch-metadata`'s `updated-dependencies-json`, as far as the tool reads it. */
data class DependabotDependency(
    val name: String,
    val packageEcosystem: String,
    val prevVersion: String,
    val newVersion: String,
)

/**
 * Dependabot's PRs (concept section 3): recognized by an author in [botLogins] and the `dependabot/` branch. The changes
 * come from the [metadata] that `dependabot/fetch-metadata` read in the user's workflow, every entry of a group; the
 * release notes from the PR body. Dependabot doesn't tolerate foreign commits on its branches, so the fixes go into a
 * companion PR.
 */
class DependabotSource(
    private val botLogins: Set<String>,
    private val metadata: List<DependabotDependency>?,
) : UpdateSource {
    override val name = "dependabot"
    override val branchPolicy = BranchPolicy.COMPANION

    override fun recognizes(pr: PullRequest) = pr.author in botLogins && pr.headBranch.startsWith(BRANCH_PREFIX)

    // Dependabot has no setting that keeps it rebasing after foreign commits; the companion PR keeps them off its branch.
    override fun setupHint(identity: CommitIdentity): String? = null

    override fun read(
        pr: PullRequest,
        context: SourceContext,
    ): SourceUpdate {
        val dependencies =
            metadata ?: throw ToolError(
                "The Dependabot PR comes without its metadata. Run dependabot/fetch-metadata in the workflow and pass its " +
                    "updated-dependencies-json output to the action's dependabot-metadata input, as the setup guide shows " +
                    "(docs/setup.md, section 3).",
            )
        return SourceUpdate(dependencies.mapNotNull(::update), releaseNotes(pr.body))
    }

    private fun update(dependency: DependabotDependency): PackageUpdate? {
        val ecosystem = ECOSYSTEMS[dependency.packageEcosystem]
        val skip =
            when {
                ecosystem == null -> "no ecosystem for Dependabot's ${dependency.packageEcosystem}"
                dependency.name == GRADLE_WRAPPER -> "the Gradle wrapper is no package"
                dependency.prevVersion.isEmpty() || dependency.newVersion.isEmpty() -> "the metadata misses a version"
                else -> null
            }
        if (skip != null) logger.info("Skipping {}: {}", dependency.name, skip)
        return if (skip == null) PackageUpdate(ecosystem, coordinates(dependency), dependency.prevVersion, dependency.newVersion) else null
    }

    /** Maven coordinates as they are; a Gradle plugin, named by its id, as its marker artifact `<id>:<id>.gradle.plugin`. */
    private fun coordinates(dependency: DependabotDependency): String {
        val plugin = dependency.packageEcosystem == "gradle" && ':' !in dependency.name
        return if (plugin) "${dependency.name}:${dependency.name}.gradle.plugin" else dependency.name
    }

    /** Every release notes and changelog section of the body, after the line that names its update. */
    private fun releaseNotes(body: String): String? {
        val sections =
            NOTES.findAll(body).map { section ->
                val heading = HEADING.findAll(body.substring(0, section.range.first)).lastOrNull()
                listOfNotNull(heading?.let { it.value.trim() }, section.groupValues[1].trim()).joinToString("\n")
            }
        return sections.joinToString("\n\n").ifEmpty { null }
    }

    companion object {
        /** Reads `updated-dependencies-json`. Fails with a [ToolError] if it isn't that. */
        fun parseMetadata(json: String): List<DependabotDependency> {
            val entries =
                try {
                    Json.parseToJsonElement(json)
                } catch (e: SerializationException) {
                    throw invalid("not valid JSON: ${e.message}", e)
                }
            if (entries !is JsonArray) throw invalid("not a JSON array", null)
            return entries.map(::dependency)
        }

        private fun dependency(entry: JsonElement): DependabotDependency {
            val fields = entry as? JsonObject ?: throw invalid("an entry is not a JSON object", null)
            return DependabotDependency(
                string(fields, "dependencyName"),
                string(fields, "packageEcosystem"),
                string(fields, "prevVersion"),
                string(fields, "newVersion"),
            )
        }

        private fun string(
            fields: JsonObject,
            name: String,
        ): String {
            val value = fields[name] ?: throw invalid("an entry has no $name", null)
            if (value !is JsonPrimitive || !value.isString) throw invalid("$name is not a string", null)
            return value.content
        }

        private fun invalid(
            reason: String,
            cause: Throwable?,
        ) = ToolError("The Dependabot metadata is not fetch-metadata's updated-dependencies-json: $reason", cause)
    }
}
