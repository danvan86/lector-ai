package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.ToolError
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.StringReader

// A plugin without a group is one of Maven's own.
private const val DEFAULT_PLUGIN_GROUP = "org.apache.maven.plugins"

/**
 * Reads the output of `help:effective-pom`: the model after Maven resolved parents, imported BOMs, and properties.
 * So versions that a parent manages are there too, and the code doesn't need to compile.
 */
object EffectivePom {
    /**
     * The version of every parent, dependency, and build plugin of every module, as `group:artifact → version`. The
     * build's own modules are not packages. Management sections are left out: they list versions nothing may use.
     */
    fun versions(xml: String): Map<String, String> {
        val root = parse(xml)
        val projects = if (root.tagName == "projects") children(root, "project") else listOf(root)
        val own = projects.map { coordinates(it, null) }.toSet()
        return projects
            .flatMap { packages(it) }
            .filter { (coordinates, _) -> coordinates !in own }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, versions) -> versions.maxWith(MavenVersionScheme::compare) }
    }

    private fun packages(project: Element): List<Pair<String, String>> {
        val parent = children(project, "parent")
        val dependencies = children(project, "dependencies").flatMap { children(it, "dependency") }
        val plugins = children(project, "build").flatMap { children(it, "plugins") }.flatMap { children(it, "plugin") }
        return (parent + dependencies).mapNotNull { versioned(it, null) } + plugins.mapNotNull { versioned(it, DEFAULT_PLUGIN_GROUP) }
    }

    private fun versioned(
        element: Element,
        defaultGroup: String?,
    ): Pair<String, String>? = text(element, "version")?.let { coordinates(element, defaultGroup) to it }

    private fun coordinates(
        element: Element,
        defaultGroup: String?,
    ) = "${text(element, "groupId") ?: defaultGroup}:${text(element, "artifactId")}"

    private fun text(
        element: Element,
        name: String,
    ): String? {
        val child = children(element, name).firstOrNull() ?: return null
        return child.textContent.trim()
    }

    private fun children(
        element: Element,
        name: String,
    ): List<Element> {
        val nodes = element.childNodes
        return (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == name }
    }

    private fun parse(xml: String): Element =
        try {
            xmlDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
        } catch (e: SAXException) {
            throw ToolError("Can't read Maven's effective POM: ${e.message}", e)
        }
}
