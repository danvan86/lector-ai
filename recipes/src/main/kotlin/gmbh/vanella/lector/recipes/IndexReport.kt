package gmbh.vanella.lector.recipes

/**
 * What the build put into the index, and what it left out. [artifacts] holds every artifact that contains recipes,
 * with its license and the number of its recipes in the index.
 */
data class IndexReport(
    val recipes: Int,
    val entries: Int,
    val skipped: Map<String, Int>,
    val artifacts: Map<RecipeArtifact, Int>,
) {
    /** The report as Markdown. [source] names what the recipes came from, e.g. the BOM's coordinates. */
    fun render(source: String): String =
        buildString {
            appendLine("# Recipe index report")
            appendLine()
            appendLine("Generated from `$source`.")
            appendLine()
            appendLine("- Indexed recipes: $recipes")
            appendLine("- Index entries: $entries")
            appendLine("- Skipped leaves: ${skipped.values.sum()}")
            appendLine()
            appendLine("## Skipped leaves by reason")
            appendLine()
            if (skipped.isEmpty()) {
                appendLine("None.")
            } else {
                appendLine("| Reason | Leaves |")
                appendLine("|---|---|")
                skipped.entries.sortedByDescending { it.value }.forEach { (reason, count) -> appendLine("| $reason | $count |") }
            }
            appendLine()
            appendLine("## Recipe artifacts")
            appendLine()
            appendLine("| Artifact | License | Indexed recipes |")
            appendLine("|---|---|---|")
            artifacts.entries.sortedBy { it.key.coordinates }.forEach { (artifact, count) ->
                appendLine("| ${artifact.coordinates} | ${artifact.license} | $count |")
            }
        }
}
