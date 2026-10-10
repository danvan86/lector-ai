package gmbh.vanella.lector.recipes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One leaf of a recipe: [recipe] moves the packages that match [packagePattern] (a glob, opaque otherwise) to a version
 * that [selector] allows. [depth] is how deep the leaf sits in the recipe's tree, `1` for a direct child. [artifact] is
 * what the recipe runner fetches to run [recipe], [license] the license of that artifact.
 */
data class IndexEntry(
    val ecosystem: String,
    val packagePattern: String,
    val selector: String,
    val recipe: String,
    val depth: Int,
    val artifact: String,
    val license: String,
)

/** The index as JSON: `{"entries": [...]}`. The field names stay generic, for every ecosystem. */
object IndexFormat {
    fun write(entries: List<IndexEntry>): String =
        buildJsonObject {
            put("entries", buildJsonArray { entries.forEach { add(json(it)) } })
        }.toString()

    /** Fails with an [IllegalArgumentException] if [json] isn't a valid index. */
    fun read(json: String): List<IndexEntry> =
        try {
            Json
                .parseToJsonElement(json)
                .jsonObject
                .getValue("entries")
                .jsonArray
                .map { entry(it.jsonObject) }
        } catch (e: IllegalArgumentException) {
            // Also a `SerializationException`: the JSON doesn't parse, or has the wrong shape.
            throw IllegalArgumentException("Malformed recipe index: ${e.message}", e)
        } catch (e: NoSuchElementException) {
            throw IllegalArgumentException("Malformed recipe index: ${e.message}", e)
        }

    private fun json(entry: IndexEntry) =
        JsonObject(
            mapOf(
                "ecosystem" to JsonPrimitive(entry.ecosystem),
                "package" to JsonPrimitive(entry.packagePattern),
                "selector" to JsonPrimitive(entry.selector),
                "recipe" to JsonPrimitive(entry.recipe),
                "depth" to JsonPrimitive(entry.depth),
                "artifact" to JsonPrimitive(entry.artifact),
                "license" to JsonPrimitive(entry.license),
            ),
        )

    private fun entry(json: JsonObject): IndexEntry {
        fun text(name: String) = json.getValue(name).jsonPrimitive.content
        return IndexEntry(
            ecosystem = text("ecosystem"),
            packagePattern = text("package"),
            selector = text("selector"),
            recipe = text("recipe"),
            depth = json.getValue("depth").jsonPrimitive.int,
            artifact = text("artifact"),
            license = text("license"),
        )
    }
}
