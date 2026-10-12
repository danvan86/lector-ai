package gmbh.vanella.lector.core

/**
 * The language knowledge of an ecosystem (concept section 7): what counts as a method, a test, an assertion, a skip, a
 * suppression, or a stub in a source file. The guardrails in the core work only on these facts, so a new language needs
 * a grammar and queries in its adapter, not new rules.
 */
fun interface LanguageSupport {
    /** The facts about [content], the text of [file] (relative to the repository root), or null if no language here has [file]. */
    fun facts(
        file: String,
        content: String,
    ): SourceFacts?
}

/** Lines [first] to [last] of a file, 1-based and inclusive. */
data class LineRange(
    val first: Int,
    val last: Int,
) {
    operator fun contains(line: Int) = line in first..last

    override fun toString() = if (first == last) "$first" else "$first-$last"
}

/**
 * What the guardrails know about one source file. Each list is in source order.
 *
 * - [methods] are every method or function, test methods included.
 * - [parseErrors] are the places the parser couldn't read. Facts near them can't be trusted.
 */
data class SourceFacts(
    val methods: List<Method> = emptyList(),
    val tests: List<TestMethod> = emptyList(),
    val skips: List<Fragment> = emptyList(),
    val suppressions: List<Fragment> = emptyList(),
    val stubs: List<Fragment> = emptyList(),
    val parseErrors: List<LineRange> = emptyList(),
)

/**
 * A method or function, by its containing type ([type], e.g. `Outer.Inner`, empty at the top level) and [name]. Its
 * [body] is the text from its first to its last statement, empty if it has none: its logic, whatever its name.
 */
data class Method(
    val type: String,
    val name: String,
    val lines: LineRange,
    val body: String,
)

/** A test method, by its containing type and name, with the [assertions] in it. */
data class TestMethod(
    val type: String,
    val name: String,
    val lines: LineRange,
    val assertions: List<Fragment>,
)

/** A piece of source, like an assertion or a suppression, with its text. */
data class Fragment(
    val lines: LineRange,
    val text: String,
)
