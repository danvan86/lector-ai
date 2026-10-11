package gmbh.vanella.lector.ecosystems.jvm

import java.util.Properties

/**
 * The Maven plugins the adapter calls by coordinates (`group:artifact:version`), pinned in the version catalog and
 * generated into a resource at build time. Called by coordinates, they need no change to the project's `pom.xml`.
 */
data class MavenPlugins(
    val rewrite: String,
    val help: String,
) {
    companion object {
        fun load(resource: String = "maven-plugins.properties"): MavenPlugins {
            val stream = checkNotNull(MavenPlugins::class.java.getResourceAsStream(resource)) { "$resource is missing from the classpath" }
            val properties = stream.use { Properties().apply { load(it) } }

            fun plugin(name: String) = checkNotNull(properties.getProperty(name)) { "$resource has no $name plugin" }
            return MavenPlugins(rewrite = plugin("rewrite"), help = plugin("help"))
        }
    }
}
