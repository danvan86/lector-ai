package gmbh.vanella.lector.ecosystems.typescript

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.readText

@OptIn(ExperimentalSerializationApi::class)
private val JSONC =
    Json {
        allowComments = true
        allowTrailingComma = true
    }

/**
 * What the adapter reads from a `tsconfig.json`, which is JSON with comments and trailing commas: whether it has
 * project [references], and whether it [compilesNothing] on its own (an empty `files` list, no `include`, and no base, as the
 * root of a workspace often has). A file that can't be read is neither: `tsc` reports what is wrong with it.
 */
internal data class Tsconfig(
    val references: Boolean,
    val compilesNothing: Boolean,
) {
    companion object {
        fun read(file: Path): Tsconfig {
            val json =
                try {
                    JSONC.parseToJsonElement(file.readText()) as? JsonObject
                } catch (_: SerializationException) {
                    null
                } ?: return Tsconfig(references = false, compilesNothing = false)
            val references = (json["references"] as? JsonArray).orEmpty().isNotEmpty()
            val emptyFiles = (json["files"] as? JsonArray)?.isEmpty() == true
            // `extends` brings the base's `include`, which an empty `files` doesn't override.
            return Tsconfig(references, emptyFiles && "include" !in json && "extends" !in json && !references)
        }
    }
}
