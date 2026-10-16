package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.treesitter.TSLanguage
import org.treesitter.TSNode
import org.treesitter.TSParser
import org.treesitter.TreeSitterTsx
import org.treesitter.TreeSitterTypescript
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

// Enough for the declarations a few errors need. More would only be trimmed from the request (M3's context budget).
private const val MAX_CHARS = 20_000

// The files a package's re-exports may lead through.
private const val MAX_FILES = 200

// The names an error message quotes: `Module '"x"' has no exported member 'Foo'`, `Property 'bar' does not exist`.
private val QUOTED_NAME = Regex("""'([A-Za-z_$][\w$]*)'""")

// The extensions of declaration files, by the extension of the module a re-export names.
private val DECLARATION_FILES = listOf(".d.ts", ".d.mts", ".d.cts", "/index.d.ts")

/** An `import … from "<package>…"` of a failing file: the package's [subpath] (`.` or `./sub`) and the imported [names]. */
private data class Import(
    val subpath: String,
    val names: List<String>,
)

/** A file of a package, by its path in the package, with its text. */
private data class SourceFile(
    val path: String,
    val text: String,
)

/** A top-level declaration of a declaration file: what it is named, and its whole text. */
private data class Declaration(
    val name: String,
    val text: String,
)

/**
 * The declarations of an updated package that the errors need (spec section 6), read from the installed tree, never
 * downloaded. [locate] finds the installed packages for an importing directory: in `node_modules`, or through Yarn's
 * Plug'n'Play API.
 *
 * - The types are where `tsc` finds them for most packages: the `types` condition of the imported subpath in
 *   `exports`, then `types` or `typings`, then `index.d.ts`, then the same in `@types/<name>`.
 * - Relative re-exports (`export * from './x'`, `export { a } from './x'`) are followed, so barrel files lead to the
 *   declaring file.
 * - Chosen are the declarations named in the errors' messages and in the failing files' imports from the package,
 *   read with the tree-sitter TypeScript grammar.
 */
internal class DeclarationGrounding(
    private val locate: (repo: Path, issuer: Path, names: List<String>) -> Map<String, PackageFiles>,
) {
    fun ground(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ): String {
        val root = repo.toRealPath()
        val name = change.`package`
        val failing =
            diagnostics
                .mapNotNull { it.file }
                .distinct()
                .map { root.resolve(it) }
                .filter { it.isRegularFile() }
        val imports = failing.flatMap { imports(it, name) }
        // A PR can change hundreds of packages: only those the failing files import, or the errors name, are looked up.
        val module = Regex("""['"]${Regex.escape(name)}(?:/[^'"]*)?['"]""")
        if (imports.isEmpty() && diagnostics.none { module.containsMatchIn(it.message) }) return ""
        val quoted = diagnostics.flatMap { diagnostic -> QUOTED_NAME.findAll(diagnostic.message).map { it.groupValues[1] } }
        val symbols = (imports.flatMap { it.names } + quoted).toSet()
        if (symbols.isEmpty()) return ""
        val types = typesPackage(name)
        val packages = locate(root, if (failing.isEmpty()) root else failing.first().parent, listOf(name, types))
        val subpaths = imports.map { it.subpath }.ifEmpty { listOf(".") }.distinct()
        val blocks =
            subpaths
                .flatMap { subpath ->
                    val found =
                        listOf(name to subpath, types to ".").firstNotNullOfOrNull { (pkg, sub) ->
                            val files = packages[pkg]
                            val entry = if (files == null) null else entry(files, sub)
                            if (files == null || entry == null) null else Triple(pkg, files, entry)
                        }
                    if (found == null) emptyList() else blocks(found.first, found.second, found.third, symbols)
                }.distinct()
        if (blocks.isEmpty()) return ""
        val text = blocks.fold("") { all, block -> if (all.length + block.length > MAX_CHARS) all else all + "\n\n" + block }
        return "Type declarations of $name ${change.toVersion}:$text"
    }

    /** The declarations of [symbols] that [entry] of the package [pkg] leads to, each with the file it is in. */
    private fun blocks(
        pkg: String,
        files: PackageFiles,
        entry: SourceFile,
        symbols: Set<String>,
    ) = declarations(files, entry)
        .filter { (_, declaration) ->
            declaration.name in symbols
        }.map { (file, declaration) -> "// $pkg/$file\n${declaration.text}" }

    /** The declaration file of [subpath] in a package, by `exports`, then `types` or `typings`, then `index.d.ts`. */
    private fun entry(
        files: PackageFiles,
        subpath: String,
    ): SourceFile? {
        val manifest =
            try {
                Json.parseToJsonElement(files.read(PACKAGE_JSON).orEmpty())
            } catch (_: SerializationException) {
                null
            } as? JsonObject
        val sub = subpath.removePrefix("./")
        val candidates =
            listOfNotNull(exportsTypes(manifest?.get("exports"), subpath)) +
                if (subpath == ".") {
                    listOfNotNull(manifest?.string("types"), manifest?.string("typings"), "index.d.ts")
                } else {
                    listOf("$sub.d.ts", "$sub/index.d.ts")
                }
        return read(files, candidates)
    }

    /** The `types` condition of [subpath] in `exports`, also nested in `import` or `require`. Without subpaths, `exports` is the root's. */
    private fun exportsTypes(
        exports: JsonElement?,
        subpath: String,
    ): String? {
        val entry =
            when {
                exports is JsonObject && exports.keys.any { it.startsWith(".") } -> exports[subpath]
                subpath == "." -> exports
                else -> null
            }
        return typesCondition(entry)
    }

    private fun typesCondition(entry: JsonElement?): String? =
        when (entry) {
            is JsonObject -> {
                val types = entry["types"]
                if (types != null) firstString(types) else entry.values.firstNotNullOfOrNull(::typesCondition)
            }

            is JsonArray -> {
                entry.firstNotNullOfOrNull(::typesCondition)
            }

            is JsonPrimitive, null -> {
                null
            }
        }

    private fun firstString(element: JsonElement): String? =
        when (element) {
            is JsonObject -> element.values.firstNotNullOfOrNull(::firstString)
            is JsonArray -> element.firstNotNullOfOrNull(::firstString)
            is JsonPrimitive -> element.text()
        }

    /** The declarations of [entry] and of the files its relative re-exports lead to, with their files. */
    private fun declarations(
        files: PackageFiles,
        entry: SourceFile,
    ): List<Pair<String, Declaration>> {
        val queue = ArrayDeque(listOf(entry))
        val seen = mutableSetOf<String>()
        val found = mutableListOf<Pair<String, Declaration>>()
        while (queue.isNotEmpty() && seen.size < MAX_FILES) {
            val file = queue.removeFirst()
            if (!seen.add(file.path)) continue
            val (declarations, reExports) = DeclarationFile.read(file.text)
            found += declarations.map { file.path to it }
            queue += reExports.mapNotNull { module -> resolve(files, file.path, module) }
        }
        return found
    }

    /** The declaration file of the relative module [module] that [from] re-exports, if the package has it. */
    private fun resolve(
        files: PackageFiles,
        from: String,
        module: String,
    ): SourceFile? {
        if (!module.startsWith(".")) return null
        val base = from.substringBeforeLast('/', "") + "/" + module.replace(Regex("""\.[cm]?js$"""), "")
        return read(files, DECLARATION_FILES.map { base + it })
    }

    /** The first of [candidates] the package has, with its text. */
    private fun read(
        files: PackageFiles,
        candidates: List<String>,
    ) = candidates.mapNotNull(::normalizePackagePath).firstNotNullOfOrNull { path -> files.read(path)?.let { SourceFile(path, it) } }

    /** The imports of [name] in the source [file], by subpath. */
    private fun imports(
        file: Path,
        name: String,
    ): List<Import> {
        val tree = Parsed(if (file.toString().endsWith(".tsx")) TreeSitterTsx() else TreeSitterTypescript(), file.readText())
        // `import x = require("…")` has no module path of its own.
        val statements = tree.root.children().filter { it.type == "import_statement" && !it.getChildByFieldName("source").isNull }
        return statements.mapNotNull { statement ->
            val module = tree.text(statement.getChildByFieldName("source")).trim('"', '\'')
            val subpath =
                if (module == name) {
                    "."
                } else if (module.startsWith("$name/")) {
                    "./" + module.removePrefix("$name/")
                } else {
                    null
                }
            subpath?.let { Import(it, statement.descendants("import_specifier").map { s -> tree.text(s.getChildByFieldName("name")) }) }
        }
    }
}

/** What a declaration file declares at its top level, and the modules it re-exports. */
private object DeclarationFile {
    fun read(text: String): Pair<List<Declaration>, List<String>> {
        val declarations = mutableListOf<Declaration>()
        val reExports = mutableListOf<String>()
        val tree = Parsed(TreeSitterTypescript(), text)
        for (node in tree.root.children()) {
            val source = node.getChildByFieldName("source")
            val declared = if (node.type == "export_statement") node.getChildByFieldName("declaration") else node
            when {
                node.type == "export_statement" && !source.isNull -> reExports += tree.text(source).trim('"', '\'')
                !declared.isNull -> name(declared, tree)?.let { declarations += Declaration(it, tree.text(node)) }
            }
        }
        return declarations to reExports
    }

    /** The name a declaration declares: a function, class, interface, type, enum, namespace, or the first variable. */
    private fun name(
        node: TSNode,
        tree: Parsed,
    ): String? {
        val declaration = if (node.type == "ambient_declaration") node.getNamedChild(0) else node
        val named = declaration.getChildByFieldName("name")
        if (!named.isNull) return tree.text(named)
        val variable = declaration.descendants("variable_declarator").firstOrNull() ?: return null
        return tree.text(variable.getChildByFieldName("name"))
    }
}

/** [text] parsed with [language]. tree-sitter counts in UTF-8 bytes, so the text is encoded once. */
private class Parsed(
    language: TSLanguage,
    text: String,
) {
    private val bytes = text.toByteArray(Charsets.UTF_8)
    val root: TSNode =
        TSParser()
            .apply { setLanguage(language) }
            .parseString(null, text)
            .rootNode

    fun text(node: TSNode) = String(bytes, node.startByte, node.endByte - node.startByte, Charsets.UTF_8)
}

private fun TSNode.children() = (0 until namedChildCount).map { getNamedChild(it) }

/** The nodes of [type] below this one, in source order. */
private fun TSNode.descendants(type: String): List<TSNode> =
    children().flatMap {
        if (it.type ==
            type
        ) {
            listOf(it)
        } else {
            it.descendants(type)
        }
    }

/** The `@types` package of [name]: `@types/lodash`, and `@types/scope__pkg` for `@scope/pkg`. */
internal fun typesPackage(name: String) = "@types/" + name.removePrefix("@").replace("/", "__")
