package gmbh.vanella.lector.ecosystems.shared

import gmbh.vanella.lector.core.Fragment
import gmbh.vanella.lector.core.LineRange
import gmbh.vanella.lector.core.Method
import gmbh.vanella.lector.core.SourceFacts
import gmbh.vanella.lector.core.TestMethod
import org.treesitter.TSLanguage
import org.treesitter.TSNode
import org.treesitter.TSParser
import org.treesitter.TSQuery
import org.treesitter.TSQueryCursor
import org.treesitter.TSQueryMatch

/**
 * The facts about the sources of one language, from its tree-sitter grammar and the query files in `queries/<name>/`,
 * a resource directory next to the class [resources]. The queries hold the language knowledge. This class only reads
 * their captures:
 *
 * - `types.scm`: `@type` with its `@name`, the types around methods.
 * - `methods.scm`, `tests.scm`: `@method` and `@test`, each with its `@name`. A method may have a `@body`: a block in
 *   braces, or Kotlin's `= expression`.
 * - `assertions.scm`: `@assertion`, a call that asserts, and `@statement`. An assertion's text is that of the innermost
 *   statement around it, so a chained assertion includes its expected value.
 * - `skips.scm`, `suppressions.scm`, `stubs.scm`: `@skip`, `@suppression`, `@stub`.
 *
 * Native memory is freed by tree-sitter-ng's cleaners when the objects are collected.
 */
class TreeSitterLanguage(
    name: String,
    private val language: TSLanguage,
    resources: Class<*>,
) {
    private val queries = QUERIES.associateWith { TSQuery(language, queryText(resources, name, it)) }

    fun facts(content: String): SourceFacts {
        val parser = TSParser()
        parser.setLanguage(language)
        val source = Source(content, parser.parseString(null, content).rootNode)
        val types = source.matches(queries.getValue("types"))
        val typeOf = { node: TSNode ->
            types.filter { it.getValue("type") encloses node }.joinToString(".") { source.text(it.getValue("name")) }
        }
        val assertions = source.matches(queries.getValue("assertions"))
        val statements = assertions.mapNotNull { it["statement"] }
        val assertion = { call: TSNode -> source.fragment(statements.lastOrNull { it encloses call } ?: call) }
        return SourceFacts(
            methods =
                source.matches(queries.getValue("methods")).map {
                    val method = it.getValue("method")
                    Method(typeOf(method), source.text(it.getValue("name")), lines(method), it["body"]?.let(source::inner).orEmpty())
                },
            tests =
                source.matches(queries.getValue("tests")).distinctBy { it.getValue("test").startByte }.map { match ->
                    val test = match.getValue("test")
                    val calls = assertions.mapNotNull { it["assertion"] }.filter { test encloses it }
                    TestMethod(typeOf(test), source.text(match.getValue("name")), lines(test), calls.map(assertion))
                },
            skips = source.fragments(queries.getValue("skips"), "skip"),
            suppressions = source.fragments(queries.getValue("suppressions"), "suppression"),
            stubs = source.fragments(queries.getValue("stubs"), "stub"),
            parseErrors = errors(source.root),
        )
    }

    private class Source(
        val content: String,
        val root: TSNode,
    ) {
        // tree-sitter counts in UTF-8 bytes.
        private val bytes = content.toByteArray(Charsets.UTF_8)

        /** Every match of [query], as capture name to node, in source order. */
        fun matches(query: TSQuery): List<Map<String, TSNode>> {
            val cursor = TSQueryCursor()
            cursor.exec(query, root, content)
            return generateSequence { TSQueryMatch().takeIf(cursor::nextMatch) }
                .map { match -> match.captures.associate { query.getCaptureNameForId(it.index) to it.node } }
                .toList()
        }

        fun fragments(
            query: TSQuery,
            capture: String,
        ) = matches(query).map { it.getValue(capture) }.sortedBy { it.startByte }.map(::fragment)

        fun fragment(node: TSNode) = Fragment(lines(node), text(node))

        /** The text of the body [node] without its braces (`{ … }`) or its `=` (Kotlin's `= expression`). */
        fun inner(node: TSNode) =
            text(node)
                .removeSurrounding("{", "}")
                .removePrefix("=")
                .trim()

        fun text(node: TSNode) = String(bytes, node.startByte, node.endByte - node.startByte, Charsets.UTF_8)
    }
}

private val QUERIES = listOf("types", "methods", "tests", "assertions", "skips", "suppressions", "stubs")

private fun queryText(
    resources: Class<*>,
    language: String,
    query: String,
): String {
    val path = "queries/$language/$query.scm"
    return checkNotNull(resources.getResource(path)) { "No query file $path" }.readText()
}

private fun lines(node: TSNode) = LineRange(node.startPoint.row + 1, node.endPoint.row + 1)

private infix fun TSNode.encloses(other: TSNode) = startByte <= other.startByte && other.endByte <= endByte

/** The nodes the parser couldn't read (`ERROR`) or had to make up (`MISSING`). */
private fun errors(node: TSNode): List<LineRange> =
    when {
        node.isError || node.isMissing -> listOf(lines(node))
        node.hasError() -> (0 until node.childCount).flatMap { errors(node.getChild(it)) }
        else -> emptyList()
    }
