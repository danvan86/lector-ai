package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.UpdateType
import gmbh.vanella.lector.core.VersionScheme

// `1.2.3-rc.1+build`, with a leading `v` and missing numbers allowed: `v2`, `2.1`.
// At most nine digits per number: they fit an Int. A longer one is no version.
private val VERSION = Regex("""v?(\d{1,9})(?:\.(\d{1,9}))?(?:\.(\d{1,9}))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?""")

// A version in a range: numbers may be `x`, `X`, or `*` (any), or missing.
private val PARTIAL = Regex("""v?(\d+|[xX*])?(?:\.(\d+|[xX*]))?(?:\.(\d+|[xX*]))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?""")

// Longest first, so `<=` isn't read as `<`.
private val OPERATORS = listOf("<=", ">=", "~>", "<", ">", "=", "^", "~")

// The regex group of a version's pre-release.
private const val PRE_RELEASE = 4
private val OPERATOR_SPACE = Regex("""(<=|>=|<|>|=|\^|~>?)\s+""")
private val HYPHEN = Regex("""^(\S+)\s+-\s+(\S+)$""")
private val WHITESPACE = Regex("""\s+""")

/**
 * The npm ecosystem's versions: semver 2.0. Pre-releases sort before their release, build metadata is ignored. Text
 * that isn't a version sorts after every version. A range (`^2.1`) reads as its version where one version is needed.
 * Selectors are npm ranges (`^1.2`, `>=18 <23`, `1.x || 2.x`, `1.0 - 2.0`), as `engines` and `package.json` write them.
 */
object SemverScheme : VersionScheme {
    override fun compare(
        a: String,
        b: String,
    ) = compareParsed(Semver.loose(a), Semver.loose(b)) ?: a.compareTo(b)

    override fun contains(
        selector: String,
        version: String,
    ): Boolean {
        val target = Semver.parse(version) ?: return false
        return NpmRange.parse(selector)?.contains(target) == true
    }

    override fun compareSelectors(
        a: String,
        b: String,
    ): Int {
        val rangeA = NpmRange.parse(a)
        val rangeB = NpmRange.parse(b)
        if (rangeA == null || rangeB == null) return compareValues(rangeA == null, rangeB == null).takeIf { it != 0 } ?: a.compareTo(b)
        val boundA = rangeA.upperBound()
        val boundB = rangeB.upperBound()
        // No upper bound sorts last.
        if (boundA == null || boundB == null) return compareValues(boundA == null, boundB == null)
        return boundA.compareTo(boundB)
    }

    override fun updateType(
        from: String,
        to: String,
    ): UpdateType {
        val before = Semver.loose(from)
        val after = Semver.loose(to)
        return when {
            before == null || after == null -> UpdateType.OTHER
            before.major != after.major -> UpdateType.MAJOR
            before.minor != after.minor -> UpdateType.MINOR
            before.patch != after.patch -> UpdateType.PATCH
            else -> UpdateType.OTHER
        }
    }

    /** Versions in their order, a version before text that is none. Null if neither is a version. */
    private fun compareParsed(
        a: Semver?,
        b: Semver?,
    ): Int? =
        when {
            a != null && b != null -> a.compareTo(b)
            a == null && b == null -> null
            else -> compareValues(a == null, b == null)
        }
}

/** A semver version. Missing numbers are 0. */
internal data class Semver(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val pre: List<String> = emptyList(),
) : Comparable<Semver> {
    override fun compareTo(other: Semver): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }).takeIf { it != 0 } ?: comparePre(pre, other.pre)

    /** The same numbers, without a pre-release. */
    val release get() = copy(pre = emptyList())

    companion object {
        fun parse(text: String): Semver? {
            val match = VERSION.matchEntire(text.trim()) ?: return null
            val numbers = match.groupValues
            return Semver(
                numbers[1].toInt(),
                numbers[2].toIntOrNull() ?: 0,
                numbers[PATCH + 1].toIntOrNull() ?: 0,
                preRelease(numbers[PRE_RELEASE]),
            )
        }

        /** The version in [text], also behind a range operator: `^2.1` → `2.1.0`. */
        fun loose(text: String) = parse(text.trim().trimStart('^', '~', '=', '>', '<', ' '))
    }
}

/** Semver's pre-release order: a release is higher than its pre-releases; numbers compare as numbers, below words. */
private fun comparePre(
    a: List<String>,
    b: List<String>,
): Int {
    if (a.isEmpty() || b.isEmpty()) return compareValues(a.isEmpty(), b.isEmpty())
    val order = a.zip(b).map { (x, y) -> compareIdentifiers(x, y) }.firstOrNull { it != 0 }
    return order ?: a.size.compareTo(b.size)
}

private fun compareIdentifiers(
    x: String,
    y: String,
): Int {
    val numberX = x.all(Char::isDigit)
    val numberY = y.all(Char::isDigit)
    // Numbers of any length: the longer is higher (semver forbids leading zeros), the same length compares by digits.
    return when {
        numberX && numberY -> compareValuesBy(x, y, { it.length }, { it })
        numberX -> -1
        numberY -> 1
        else -> x.compareTo(y)
    }
}

/** The identifiers of a pre-release, `rc.1` → `[rc, 1]`. */
private fun preRelease(text: String) = if (text.isEmpty()) emptyList() else text.split('.')

/** The highest version a range allows, and whether it excludes that version itself. At the same version, excluding sorts first. */
internal data class Bound(
    val version: Semver,
    val exclusive: Boolean,
) : Comparable<Bound> {
    override fun compareTo(other: Bound) = compareValuesBy(this, other, { it.version }, { !it.exclusive })
}

/** One comparison, e.g. `>= 1.2.0`. */
internal data class Comparison(
    val operator: String,
    val version: Semver,
) {
    fun matches(target: Semver): Boolean {
        val order = target.compareTo(version)
        return when (operator) {
            ">=" -> order >= 0
            ">" -> order > 0
            "<=" -> order <= 0
            "<" -> order < 0
            else -> order == 0
        }
    }

    /** The highest version this comparison allows, or null if it allows any higher one. */
    fun bound() = if (operator in setOf("<", "<=", "=")) Bound(version, operator == "<") else null
}

/**
 * An npm range: alternatives (`||`), each a set of comparisons that must all hold. A pre-release is only in a set that
 * names a pre-release of the same version, as npm has it.
 */
internal class NpmRange private constructor(
    private val alternatives: List<List<Comparison>>,
) {
    fun contains(version: Semver) =
        alternatives.any { set ->
            set.all { it.matches(version) } &&
                (version.pre.isEmpty() || set.any { it.version.pre.isNotEmpty() && it.version.release == version.release })
        }

    /** The highest version the range allows, or null if it has no upper bound. */
    fun upperBound(): Bound? {
        val bounds = alternatives.map { set -> set.mapNotNull { it.bound() }.minOrNull() }
        return if (null in bounds) null else bounds.filterNotNull().max()
    }

    companion object {
        /** The range [text] means, or null if it isn't one. */
        fun parse(text: String): NpmRange? {
            val alternatives = text.split("||").map { set(it.trim()) }
            return if (null in alternatives) null else NpmRange(alternatives.filterNotNull())
        }

        private fun set(text: String): List<Comparison>? {
            val hyphen = HYPHEN.matchEntire(text)
            if (hyphen != null) {
                val low = partial(hyphen.groupValues[1])
                val high = partial(hyphen.groupValues[2])
                return if (low == null || high == null) null else listOf(Comparison(">=", low.lowest())) + high.atMost()
            }
            // `>= 1.2` is one comparison: join operators to their versions first.
            val tokens = text.replace(OPERATOR_SPACE, "$1").split(WHITESPACE).filter { it.isNotEmpty() }
            val comparisons = tokens.map(::comparisons)
            return if (null in comparisons) null else comparisons.filterNotNull().flatten()
        }

        private fun comparisons(token: String): List<Comparison>? {
            val operator = OPERATORS.firstOrNull { token.startsWith(it) }.orEmpty()
            val version = partial(token.removePrefix(operator)) ?: return null
            return when (operator) {
                "", "=" -> version.exactly()
                "^" -> version.caret()
                "~", "~>" -> version.tilde()
                ">=" -> listOf(Comparison(">=", version.lowest()))
                "<" -> listOf(Comparison("<", version.lowest()))
                ">" -> version.above()
                else -> version.atMost()
            }
        }

        private fun partial(text: String): Partial? {
            if (text.isEmpty()) return null
            val match = PARTIAL.matchEntire(text) ?: return null
            val groups = match.groupValues
            val numbers = groups.subList(1, PRE_RELEASE).map { it.toIntOrNull() }
            return Partial(numbers.takeWhile { it != null }.filterNotNull(), preRelease(groups[PRE_RELEASE]))
        }
    }
}

/** A version in a range with only its first [numbers] given: `1.2` is `1.2.x`, `*` gives none. */
private data class Partial(
    val numbers: List<Int>,
    val pre: List<String>,
) {
    private val complete = numbers.size == PATCH + 1

    fun lowest() = Semver(numbers.getOrElse(0) { 0 }, numbers.getOrElse(1) { 0 }, numbers.getOrElse(2) { 0 }, pre)

    /** The first version after every version this incomplete partial covers: `1.2` → `1.3.0`. Null if it covers all versions. */
    private fun next() = if (numbers.isEmpty()) null else bump(numbers)

    private fun below(version: Semver?) = listOfNotNull(version?.let { Comparison("<", it) })

    fun exactly() = if (complete) listOf(Comparison("=", lowest())) else listOf(Comparison(">=", lowest())) + below(next())

    fun atMost() = if (complete) listOf(Comparison("<=", lowest())) else below(next())

    fun above() =
        when {
            complete -> listOf(Comparison(">", lowest()))
            numbers.isEmpty() -> listOf(Comparison("<", Semver(0, 0, 0)))
            else -> listOf(Comparison(">=", bump(numbers)))
        }

    /** `~1.2.3` → `<1.3.0`, `~1` → `<2.0.0`. */
    fun tilde() = listOf(Comparison(">=", lowest())) + below(if (numbers.isEmpty()) null else bump(numbers.take(2)))

    /** `^1.2.3` → `<2.0.0`, `^0.2.3` → `<0.3.0`, `^0.0.3` → `<0.0.4`: the first non-zero number given may not change. */
    fun caret(): List<Comparison> {
        val significant = numbers.indexOfFirst { it > 0 }.let { if (it < 0) numbers.lastIndex else it }
        val upper =
            when (significant) {
                -1 -> null
                PATCH -> Semver(0, 0, numbers[PATCH] + 1)
                else -> bump(numbers.take(significant + 1))
            }
        return listOf(Comparison(">=", lowest())) + below(upper)
    }
}

private const val PATCH = 2

/** The version after the last of [numbers] (one or two): `[1]` → `2.0.0`, `[1, 2]` → `1.3.0`. */
private fun bump(numbers: List<Int>) = if (numbers.size == 1) Semver(numbers[0] + 1, 0, 0) else Semver(numbers[0], numbers[1] + 1, 0)
