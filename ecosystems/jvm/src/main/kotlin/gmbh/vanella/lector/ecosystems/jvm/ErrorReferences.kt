package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

private val IMPORT = Regex("""^\s*import\s+(?:static\s+)?([\w.]+?)(\.\*)?(?:\s+as\s+\w+)?\s*;?\s*$""", RegexOption.MULTILINE)
private val QUALIFIED_CLASS = Regex("""\b(?:[a-z_][\w]*\.)+[A-Z][\w$]*""")

/** What the [diagnostics] of the erroring files in [repo] refer to, for grounding. */
internal class ErrorReferences(
    repo: Path,
    diagnostics: List<Diagnostic>,
) {
    private val imports =
        diagnostics
            .mapNotNull { it.file }
            .toSet()
            .map { repo.resolve(it) }
            .filter { it.isRegularFile() }
            .flatMap { file -> IMPORT.findAll(file.readText()).map { it.groupValues[1] + it.groupValues[2] } }
            .toSet()
    private val named = diagnostics.flatMap { diagnostic -> QUALIFIED_CLASS.findAll(diagnostic.message).map { it.value } }.toSet()

    /** The symbols and packages the compilers can't find. */
    val missing =
        diagnostics
            .flatMap {
                listOfNotNull(
                    ClusterKeys.missingSymbol(it.message),
                    ClusterKeys.missingPackage(it.message),
                )
            }.toSet()

    /** Imports of the erroring files (`a.b.C`, `a.b.*`) and qualified class names in the messages. */
    val all = imports + named

    /**
     * The imports tied to a missing symbol or package: the class a missing symbol names (`a.b.Legacy` for javac's
     * `class Legacy`), and the classes imported from a package that is gone (`mu.KotlinLogging` for kotlinc's 'mu', javac's
     * `org.example.old`).
     */
    private val tied =
        imports.filterNot { it.endsWith(".*") }.filter { ref ->
            simpleName(ref) in missing || prefixes(packageOf(ref)).any { it in missing || simpleName(it) in missing }
        }

    /** The missing symbols, and the simple names of the classes imported from a package that is gone: the new version may have them elsewhere. */
    val symbols = missing + tied.map(::simpleName)

    /** What the errors themselves name: the qualified names in the messages, and the imports tied to a missing symbol or package. */
    val cited = named + tied

    /** The classes of [index] that [names] refer to: `a.b.C.m` (a static import) is the class `a.b.C`. */
    fun classes(
        names: Set<String>,
        index: Set<String>,
    ) = names.flatMap { name -> prefixes(name).filter { it in index }.take(1) }.toSet()
}

/** `a.b.C.m` → `a.b.C.m`, `a.b.C`, `a.b`, `a`. */
internal fun prefixes(name: String) = generateSequence(name) { if ('.' in it) it.substringBeforeLast('.') else null }

internal fun packageOf(name: String) = name.substringBeforeLast('.', "")

internal fun simpleName(name: String) = name.substringAfterLast('.')
