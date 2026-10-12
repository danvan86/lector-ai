package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.ToolError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path

/** A JAR on a compile classpath, with the module (`group:artifact`) and version it belongs to. */
internal data class ClasspathJar(
    val module: String,
    val version: String,
    val file: Path,
)

/**
 * What the init script's query task reads from Gradle: the highest resolved version of every package, the JARs of the
 * compile classpaths, and the IDs of the applied plugins.
 */
internal data class GradleModel(
    val versions: Map<String, String>,
    val classpath: List<ClasspathJar>,
    val plugins: List<String>,
) {
    companion object {
        /** Reads the query task's JSON. Of several versions of a package, the highest in Maven's order wins. */
        fun parse(json: String): GradleModel =
            try {
                val model = Json.parseToJsonElement(json).jsonObject
                GradleModel(
                    versions =
                        model.field("versions").jsonObject.mapValues { (_, versions) ->
                            versions.jsonArray.map { it.jsonPrimitive.content }.maxWith(MavenVersionScheme::compare)
                        },
                    classpath =
                        model.field("classpath").jsonArray.map { jar ->
                            val fields = jar.jsonObject
                            ClasspathJar(fields.text("module"), fields.text("version"), Path.of(fields.text("file")))
                        },
                    plugins = model.field("plugins").jsonArray.map { it.jsonPrimitive.content },
                )
            } catch (e: SerializationException) {
                throw ToolError("Can't read the model of the Gradle build: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw ToolError("Can't read the model of the Gradle build: ${e.message}", e)
            }

        private fun JsonObject.field(name: String) = get(name) ?: throw IllegalArgumentException("no $name")

        private fun JsonObject.text(name: String) = field(name).jsonPrimitive.content
    }
}
