package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.LanguageSupport
import gmbh.vanella.lector.ecosystems.shared.TreeSitterLanguage
import org.treesitter.TreeSitterJava
import org.treesitter.TreeSitterKotlin

/** Java and Kotlin sources, by file extension. The grammars load on first use; the queries are in `queries/<language>/`. */
internal object JvmLanguageSupport : LanguageSupport {
    private val java by lazy { TreeSitterLanguage("java", TreeSitterJava(), JvmLanguageSupport::class.java) }
    private val kotlin by lazy { TreeSitterLanguage("kotlin", TreeSitterKotlin(), JvmLanguageSupport::class.java) }

    override fun facts(
        file: String,
        content: String,
    ) = when {
        file.endsWith(".java") -> java.facts(content)
        file.endsWith(".kt") || file.endsWith(".kts") -> kotlin.facts(content)
        else -> null
    }
}
