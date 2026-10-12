package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.RecipeMatch
import gmbh.vanella.lector.core.RecipeOutcome
import gmbh.vanella.lector.core.RecipeResult
import gmbh.vanella.lector.core.VersionChange
import gmbh.vanella.lector.core.reason
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The recipe section of the `migrate` report: which recipe ran, or why none did, and what it changed. */
internal object RecipeReport {
    fun text(
        change: DependencyChange,
        result: RecipeResult,
    ): String {
        val outcome = result.outcome
        val chosen = result.selection.chosen
        val lines =
            buildList {
                add("Recipe (${if (outcome is RecipeOutcome.Applied) "applied" else "not applied"})")
                val versions = "${change.fromVersion} -> ${change.toVersion}"
                add("  Change: ${change.`package`} $versions (${change.ecosystem}, ${updateType(change)})")
                outcome.reason(change)?.let { add("  Reason: ${it.replace("\n", "\n    ")}") }
                if (chosen != null) {
                    add("  Recipe: ${match(chosen)}")
                    add("  Artifact: ${chosen.recipe.artifact} (${chosen.recipe.license})")
                    addAll(list("Alternatives", result.selection.alternatives.map(::match)))
                }
                if (outcome is RecipeOutcome.Applied) addAll(list("Changed files", outcome.changedFiles))
                versionChanges(outcome)?.let { (title, changes) -> addAll(list(title, changes.map(::versionChange))) }
            }
        return lines.joinToString("\n")
    }

    fun json(
        change: DependencyChange,
        result: RecipeResult,
    ): JsonObject {
        val outcome = result.outcome
        return JsonObject(
            mapOf(
                "change" to
                    JsonObject(
                        mapOf(
                            "ecosystem" to JsonPrimitive(change.ecosystem),
                            "package" to JsonPrimitive(change.`package`),
                            "fromVersion" to JsonPrimitive(change.fromVersion),
                            "toVersion" to JsonPrimitive(change.toVersion),
                            "updateType" to JsonPrimitive(updateType(change)),
                        ),
                    ),
                "outcome" to JsonPrimitive(name(outcome)),
                "reason" to JsonPrimitive(outcome.reason(change)),
                "chosen" to result.selection.chosen.let { if (it == null) JsonNull else json(it) },
                "alternatives" to JsonArray(result.selection.alternatives.map(::json)),
                "changedFiles" to JsonArray((outcome as? RecipeOutcome.Applied)?.changedFiles.orEmpty().map(::JsonPrimitive)),
                "versionChanges" to JsonArray(versionChanges(outcome)?.second.orEmpty().map(::json)),
            ),
        )
    }

    /** What the recipe changed, or, for a discarded recipe, what it would have moved down. With the list's title. */
    private fun versionChanges(outcome: RecipeOutcome): Pair<String, List<VersionChange>>? =
        when (outcome) {
            is RecipeOutcome.Applied -> "Version changes" to outcome.versionChanges

            is RecipeOutcome.DowngradeDetected -> "Downgrades" to outcome.downgrades

            RecipeOutcome.NotAnUpgrade,
            is RecipeOutcome.PrOutdated,
            RecipeOutcome.NoMatch,
            RecipeOutcome.NoRecipeRunner,
            is RecipeOutcome.VersionsUnreadable,
            is RecipeOutcome.RunFailed,
            -> null
        }

    private fun updateType(change: DependencyChange) = change.updateType.name.lowercase()

    private fun name(outcome: RecipeOutcome) =
        when (outcome) {
            is RecipeOutcome.Applied -> "applied"
            RecipeOutcome.NotAnUpgrade -> "notAnUpgrade"
            is RecipeOutcome.PrOutdated -> "prOutdated"
            RecipeOutcome.NoMatch -> "noMatch"
            RecipeOutcome.NoRecipeRunner -> "noRecipeRunner"
            is RecipeOutcome.VersionsUnreadable -> "versionsUnreadable"
            is RecipeOutcome.RunFailed -> "runFailed"
            is RecipeOutcome.DowngradeDetected -> "downgradeDetected"
        }

    private fun list(
        title: String,
        items: List<String>,
    ) = listOf("  $title (${items.size}):") + items.map { "    - $it" }.ifEmpty { listOf("    none") }

    private fun match(match: RecipeMatch) = "${match.recipe.name}, leaf ${match.selector} at depth ${match.depth}"

    private fun versionChange(change: VersionChange) = "${change.`package`} ${change.before ?: "added"} -> ${change.after ?: "removed"}"

    private fun json(match: RecipeMatch) =
        JsonObject(
            mapOf(
                "name" to JsonPrimitive(match.recipe.name),
                "artifact" to JsonPrimitive(match.recipe.artifact),
                "license" to JsonPrimitive(match.recipe.license),
                "selector" to JsonPrimitive(match.selector),
                "depth" to JsonPrimitive(match.depth),
            ),
        )

    private fun json(change: VersionChange) =
        JsonObject(
            mapOf(
                "package" to JsonPrimitive(change.`package`),
                "before" to JsonPrimitive(change.before),
                "after" to JsonPrimitive(change.after),
            ),
        )
}
