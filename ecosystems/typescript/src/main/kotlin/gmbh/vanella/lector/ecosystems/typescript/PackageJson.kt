package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.ToolError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * What the adapter reads from a `package.json`. [dependencies] are the installed ones of every kind (`dependencies`,
 * `devDependencies`, `optionalDependencies`), by name; the first kind that names a package wins. [workspaces] are the
 * globs of npm and Yarn workspaces, [engines] is `engines.node`.
 */
internal data class PackageJson(
    val name: String?,
    val dependencies: Map<String, String>,
    val scripts: Map<String, String>,
    val workspaces: List<String>,
    val engines: String?,
    val packageManager: String?,
) {
    companion object {
        /** Reads [file], named [label] in errors. Fails with a [ToolError] if it isn't a JSON object. */
        fun read(
            file: Path,
            label: String,
        ): PackageJson {
            val json = jsonObject(file, label)
            // npm and Yarn take a list; Yarn classic also an object with the list in `packages`.
            val workspaces = json["workspaces"] as? JsonArray ?: (json["workspaces"] as? JsonObject)?.get("packages")
            return PackageJson(
                name = json.string("name"),
                dependencies =
                    listOf("optionalDependencies", "devDependencies", "dependencies")
                        .map { strings(json[it]) }
                        .reduce { all, kind -> all + kind },
                scripts = strings(json["scripts"]),
                workspaces = (workspaces as? JsonArray).orEmpty().mapNotNull { it.text() },
                engines = (json["engines"] as? JsonObject)?.string("node"),
                packageManager = json.string("packageManager"),
            )
        }
    }
}

/** Reads the JSON object in [file], named [label] in errors. Fails with a [ToolError] if it isn't one. */
internal fun jsonObject(
    file: Path,
    label: String,
): JsonObject {
    val json =
        try {
            Json.parseToJsonElement(file.readText())
        } catch (e: SerializationException) {
            throw ToolError("Can't read $label: ${e.message}", e)
        }
    return json as? JsonObject ?: throw ToolError("Can't read $label: it isn't a JSON object")
}

internal fun JsonObject.string(key: String) = get(key)?.text()

internal fun JsonElement.text(): String? {
    val primitive = this as? JsonPrimitive
    return if (primitive != null && primitive.isString) primitive.content else null
}

/** The string values of a JSON object, by key. Anything else is left out. */
private fun strings(element: JsonElement?): Map<String, String> =
    (element as? JsonObject).orEmpty().mapNotNull { (key, value) -> value.text()?.let { key to it } }.toMap()
