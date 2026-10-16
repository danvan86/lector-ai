package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.Diagnostic

// TS2307: `Cannot find module 'not-a-module' or its corresponding type declarations.`
private val MODULE = Regex("""Cannot find module '([^']+)'""")

// TS2305, TS2614: `Module '"./b"' has no exported member 'missing'.` TS2724: `'"./b"' has no exported member named 'presnt'.`
private val EXPORT = Regex(""""([^"]+)"' has no exported member(?: named)? '([^']+)'""")

// TS2339: `Property 'b' does not exist on type '{ a: number; }'.`
private val PROPERTY = Regex("""^Property '([^']+)' does not exist""")

private val MISSING_EXPORT = setOf("TS2305", "TS2614", "TS2724")

/**
 * The root causes of `tsc` errors (spec section 4): a module that can't be found, a module's missing export, a missing
 * property. Everything else is one cause per error code and file. Test failures and errors without a code name none.
 */
internal object TypeScriptClusterKeys {
    fun of(diagnostic: Diagnostic): String? {
        val code = diagnostic.code
        if (diagnostic.test != null || code == null) return null
        val message = diagnostic.message
        val module = MODULE.find(message)
        val export = EXPORT.find(message)
        val property = PROPERTY.find(message)
        return when {
            code == "TS2307" && module != null -> "module:${module.groupValues[1]}"
            code in MISSING_EXPORT && export != null -> "export:${export.groupValues[1]}:${export.groupValues[2]}"
            code == "TS2339" && property != null -> "property:${property.groupValues[1]}"
            else -> "$code:${diagnostic.file}"
        }
    }
}
