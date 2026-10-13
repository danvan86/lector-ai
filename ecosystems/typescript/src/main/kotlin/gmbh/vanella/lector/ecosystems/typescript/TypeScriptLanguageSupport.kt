package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.LanguageSupport
import gmbh.vanella.lector.ecosystems.shared.TreeSitterLanguage
import org.treesitter.TreeSitterJavascript
import org.treesitter.TreeSitterTsx
import org.treesitter.TreeSitterTypescript

private val TYPESCRIPT = listOf(".ts", ".mts", ".cts")
private val JAVASCRIPT = listOf(".js", ".jsx", ".mjs", ".cjs")

/**
 * TypeScript, TSX, and JavaScript sources, by file extension (spec section 8). TSX files need their own grammar: the
 * TypeScript one reads `<T>value` as a cast. TypeScript and TSX share the queries in `queries/typescript/`, JavaScript
 * has its own in `queries/javascript/`. The grammars load on first use.
 */
internal object TypeScriptLanguageSupport : LanguageSupport {
    private val typescript by lazy { TreeSitterLanguage("typescript", TreeSitterTypescript(), TypeScriptLanguageSupport::class.java) }
    private val tsx by lazy { TreeSitterLanguage("typescript", TreeSitterTsx(), TypeScriptLanguageSupport::class.java) }
    private val javascript by lazy { TreeSitterLanguage("javascript", TreeSitterJavascript(), TypeScriptLanguageSupport::class.java) }

    override fun facts(
        file: String,
        content: String,
    ) = when {
        file.endsWith(".tsx") -> tsx.facts(content)
        TYPESCRIPT.any { file.endsWith(it) } -> typescript.facts(content)
        JAVASCRIPT.any { file.endsWith(it) } -> javascript.facts(content)
        else -> null
    }
}
