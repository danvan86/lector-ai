package gmbh.vanella.lector.cli

import java.util.Properties

/** The tool's version, generated from the Gradle `version` property at build time. */
internal object AppVersion {
    fun read(resource: String = "version.properties"): String {
        val stream =
            requireNotNull(javaClass.getResourceAsStream(resource)) {
                "$resource is missing from the classpath"
            }
        val properties = stream.use { Properties().apply { load(it) } }
        return requireNotNull(properties.getProperty("version")) {
            "$resource has no version property"
        }
    }
}
