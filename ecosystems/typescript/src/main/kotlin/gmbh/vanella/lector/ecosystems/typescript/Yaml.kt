package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.ToolError
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * The documents of the YAML file [file], named [label] in errors. Fails with a [ToolError] if it isn't YAML. Lockfiles
 * of large workspaces run to megabytes, so there is no size limit.
 */
internal fun yamlDocuments(
    file: Path,
    label: String,
) = yamlDocuments(file.readText(), label)

/** The documents of the YAML [text], named [label] in errors. Fails with a [ToolError] if it isn't YAML. */
internal fun yamlDocuments(
    text: String,
    label: String,
): List<Any?> {
    val settings = LoadSettings.builder().setCodePointLimit(Int.MAX_VALUE).build()
    return try {
        Load(settings).loadAllFromString(text).toList()
    } catch (e: YamlEngineException) {
        throw ToolError("Can't read $label: ${e.message}", e)
    }
}

/** The YAML mapping [value], or an empty one if it isn't a mapping. */
internal fun mapping(value: Any?): Map<*, *> = (value as? Map<*, *>).orEmpty()
