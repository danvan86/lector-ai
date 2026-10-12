package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.ToolError
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.nio.file.Path
import kotlin.io.path.isRegularFile

// Without a declared release, the project gets the JDK most current projects build with.
private const val DEFAULT_RELEASE = 21

// `1.8` is release 8.
private const val OLD_NOTATION = "1."

private val PROPERTY_REFERENCE = Regex("""\$\{([^}]+)}""")

/**
 * Picks the JDK a Maven project builds with from the [installed] ones (release → `JAVA_HOME`): the smallest that is at
 * least the release the root POM declares in `maven.compiler.release`, else in `java.version`, else 21. A newer JDK
 * compiles older releases, so a project on 11 gets 17. Without installed JDKs, nothing is picked and the environment's
 * `JAVA_HOME` applies.
 */
internal class JdkSelection(
    private val installed: Map<Int, Path>,
) {
    /** The `JAVA_HOME` for the project at [root], or null to keep the environment's. Fails if no installed JDK is new enough. */
    fun javaHome(root: Path): Path? {
        if (installed.isEmpty()) return null
        val release = release(root)
        val jdk =
            installed.keys.filter { it >= release }.minOrNull()
                ?: throw ToolError(
                    "The project needs Java $release, but only JDKs ${installed.keys.sorted().joinToString(", ")} are installed",
                )
        return installed.getValue(jdk)
    }

    private fun release(root: Path): Int {
        val pom = root.resolve("pom.xml")
        if (!pom.isRegularFile()) return DEFAULT_RELEASE
        val properties =
            try {
                properties(xmlDocumentBuilder().parse(pom.toFile()).documentElement)
            } catch (_: SAXException) {
                return DEFAULT_RELEASE
            }
        val declared = properties["maven.compiler.release"] ?: properties["java.version"] ?: return DEFAULT_RELEASE
        val resolved = PROPERTY_REFERENCE.replace(declared) { properties[it.groupValues[1]] ?: it.value }
        return resolved.removePrefix(OLD_NOTATION).toIntOrNull() ?: DEFAULT_RELEASE
    }

    private fun properties(project: Element): Map<String, String> {
        val nodes = project.childNodes
        val properties =
            (0 until nodes.length)
                .map {
                    nodes.item(
                        it,
                    )
                }.filterIsInstance<Element>()
                .firstOrNull { it.tagName == "properties" }
        if (properties == null) return emptyMap()
        val children = properties.childNodes
        return (0 until children.length)
            .map { children.item(it) }
            .filterIsInstance<Element>()
            .associate { it.tagName to it.textContent.trim() }
    }
}
