package gmbh.vanella.lector.llm

import gmbh.vanella.lector.core.Edit
import gmbh.vanella.lector.core.Proposal
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The structure the model must answer with: a short analysis, a one-line rationale, and search/replace edits (rule 3).
 * Ollama gets the schema as `format`, so the model can't answer with anything else. The answer is checked anyway: a
 * small model can still return something that doesn't match.
 *
 * The analysis comes first so the model reasons before it writes the edits: without it, `qwen2.5-coder:7b` replaced a
 * moved method's class but forgot its import, every time. The loop doesn't use it.
 */
internal object FixSchema {
    val SCHEMA: JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("analysis") { put("type", "string") }
                putJsonObject("rationale") { put("type", "string") }
                putJsonObject("edits") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            listOf("file", "search", "replace").forEach { putJsonObject(it) { put("type", "string") } }
                        }
                        putJsonArray("required") { listOf("file", "search", "replace").forEach { add(it) } }
                    }
                }
            }
            putJsonArray("required") {
                add("analysis")
                add("rationale")
                add("edits")
            }
        }

    /** The model's answer as a [Proposal]. Code fences around the JSON are stripped. */
    fun parse(answer: String): Proposal {
        val json = json(stripFences(answer)) ?: return Proposal.Invalid("the answer is not JSON")
        if (json !is JsonObject) return Proposal.Invalid("the answer is not a JSON object")
        string(json, "analysis") ?: return Proposal.Invalid("analysis is missing or not a string")
        val rationale = string(json, "rationale") ?: return Proposal.Invalid("rationale is missing or not a string")
        val edits = json["edits"] as? JsonArray ?: return Proposal.Invalid("edits is missing or not a list")
        val parsed = edits.mapIndexed { index, element -> edit(element) ?: return Proposal.Invalid(invalidEdit(index + 1, element)) }
        return Proposal.Edits(parsed, rationale)
    }

    private fun edit(element: JsonElement): Edit? {
        if (element !is JsonObject) return null
        val file = string(element, "file") ?: return null
        val search = string(element, "search") ?: return null
        val replace = string(element, "replace") ?: return null
        return Edit(file, search, replace)
    }

    private fun invalidEdit(
        number: Int,
        element: JsonElement,
    ): String {
        if (element !is JsonObject) return "edit $number is not an object"
        val missing = listOf("file", "search", "replace").first { string(element, it) == null }
        return "edit $number: $missing is missing or not a string"
    }

    private fun string(
        json: JsonObject,
        name: String,
    ): String? {
        val value = json[name] as? JsonPrimitive
        return if (value != null && value.isString) value.content else null
    }

    /** [text] as JSON, or null if it isn't. */
    fun json(text: String): JsonElement? =
        try {
            Json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            null
        }

    private fun stripFences(answer: String): String {
        val text = answer.trim()
        if (!text.startsWith("```")) return text
        return text.substringAfter('\n').removeSuffix("```").trim()
    }
}
