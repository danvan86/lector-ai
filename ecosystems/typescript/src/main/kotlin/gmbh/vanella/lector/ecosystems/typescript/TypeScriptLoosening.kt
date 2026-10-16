package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.ToolError
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// tsconfig.json is JSON with comments and trailing commas; package.json is plain JSON, which this reads too.
@OptIn(ExperimentalSerializationApi::class)
private val LENIENT =
    Json {
        allowComments = true
        allowTrailingComma = true
    }

// The test scripts: `test`, the scripts it runs through (`test:unit`), and its `pre` and `post` scripts.
private val TEST_SCRIPT_NAME = Regex("""^(?:pre|post)?test(?:[:._-].*)?$""")

private const val NEW_INPUTS = "a new `include` or `files` list narrows the type check, which covered every file"

// The options that check more when they are on, and are off by default.
private val CHECKING = Regex("""^(?:strict\w*|noImplicit\w*|checkJs)$""")

/**
 * What a TypeScript build file's change loosens as a whole (review D1): what no added line shows. In TypeScript the
 * `test` script and the strict options are opt-in, so removing them loosens; so does narrowing what the type check or
 * the workspaces cover. Only JSON and YAML build files are read; a file that can't be read before or after tells nothing.
 */
internal object TypeScriptLoosening {
    fun of(
        file: String,
        before: String,
        after: String,
    ): List<String> {
        val name = file.substringAfterLast('/')
        return when {
            name == PACKAGE_JSON -> {
                both(before, after, ::json) { b, a ->
                    testScripts(b, a) +
                        workspaces(manifestWorkspaces(b), manifestWorkspaces(a))
                }
            }

            name.startsWith("tsconfig") -> {
                both(before, after, ::json) { b, a -> typeCheck(b, a) }
            }

            name == "pnpm-workspace.yaml" -> {
                both(before, after, ::yamlPackages) { b, a -> workspaces(b, a) }
            }

            else -> {
                emptyList()
            }
        }
    }

    private fun testScripts(
        before: JsonObject,
        after: JsonObject,
    ): List<String> {
        val kept = keys(after["scripts"])
        val removed = keys(before["scripts"]).filter { TEST_SCRIPT_NAME.matches(it) && it !in kept }
        return removed.map { "removing the `$it` script runs no tests" }
    }

    private fun typeCheck(
        before: JsonObject,
        after: JsonObject,
    ): List<String> {
        val options = before["compilerOptions"] as? JsonObject
        val kept = keys(after["compilerOptions"])
        val checking = options.orEmpty().filter { (key, value) -> CHECKING.matches(key) && value == JsonPrimitive(true) && key !in kept }
        val base = before.string("extends")
        val inputsBefore = inputs(before)
        val inputsAfter = inputs(after)
        val dropped = if (inputsBefore == null || inputsAfter == null) emptyList() else inputsBefore - inputsAfter.toSet()
        val projects = references(before) - references(after).toSet()
        return checking.keys.map { "removing `$it: true` loosens the type check" } +
            listOfNotNull(if (base != null && "extends" !in after) "removing `extends` drops the options of `$base`" else null) +
            listOfNotNull(
                when {
                    inputsBefore == null && inputsAfter != null -> NEW_INPUTS
                    dropped.isNotEmpty() -> "the type check no longer covers ${dropped.joinToString(", ") { "`$it`" }}"
                    else -> null
                },
            ) +
            projects.map { "the type check no longer covers the project `$it`" }
    }

    /** What `include` and `files` name, or null if neither is there: then the type check covers every file. */
    private fun inputs(tsconfig: JsonObject): List<String>? {
        if ("include" !in tsconfig && "files" !in tsconfig) return null
        return texts(tsconfig["include"]) + texts(tsconfig["files"])
    }

    private fun references(tsconfig: JsonObject) =
        (tsconfig["references"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("path") }

    /** npm and Yarn take a list; Yarn classic also an object with the list in `packages`. */
    private fun manifestWorkspaces(manifest: JsonObject): List<String> {
        val workspaces = manifest["workspaces"]
        return texts(if (workspaces is JsonObject) workspaces["packages"] else workspaces)
    }

    private fun workspaces(
        before: List<String>,
        after: List<String>,
    ) = (before - after.toSet()).filter { !it.startsWith("!") }.map { "the workspaces no longer include `$it`" } +
        (after - before.toSet()).filter { it.startsWith("!") }.map { "the workspaces newly exclude `$it`" }

    /** [check] on [before] and [after] as [read] reads them; nothing if either can't be read. */
    private fun <T : Any> both(
        before: String,
        after: String,
        read: (String) -> T?,
        check: (T, T) -> List<String>,
    ): List<String> {
        val old = read(before)
        val new = read(after)
        return if (old == null || new == null) emptyList() else check(old, new)
    }

    private fun yamlPackages(text: String) =
        try {
            (mapping(yamlDocuments(text, "pnpm-workspace.yaml").firstOrNull())["packages"] as? List<*>).orEmpty().filterIsInstance<String>()
        } catch (_: ToolError) {
            null
        }
}

/** The JSON object in [text], or null if it isn't one. */
private fun json(text: String) =
    try {
        LENIENT.parseToJsonElement(text) as? JsonObject
    } catch (_: SerializationException) {
        null
    }

private fun keys(element: JsonElement?) = (element as? JsonObject).orEmpty().keys

private fun texts(element: JsonElement?) = (element as? JsonArray).orEmpty().mapNotNull { it.text() }
