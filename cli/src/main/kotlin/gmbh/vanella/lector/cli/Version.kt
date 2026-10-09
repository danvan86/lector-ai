package gmbh.vanella.lector.cli

import java.util.Properties

/** The tool's version, generated from the Gradle `version` property at build time. */
internal fun appVersion(resource: String = "version.properties"): String {
    val stream =
        requireNotNull(LectorCommand::class.java.getResourceAsStream(resource)) {
            "$resource is missing from the classpath"
        }
    return stream.use { Properties().apply { load(it) } }.getProperty("version")
}
