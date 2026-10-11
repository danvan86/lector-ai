package gmbh.vanella.lector.core

/**
 * A version scheme for tests: numeric dotted versions (`1.2.3`), and selectors that are exact versions or X-ranges
 * (`1.2.x`). Just enough to drive the migration flow.
 */
object DottedVersions : VersionScheme {
    override fun compare(
        a: String,
        b: String,
    ) = compareSegments(segments(a), segments(b))

    override fun contains(
        selector: String,
        version: String,
    ) = if (selector.endsWith(".x")) version.startsWith(selector.removeSuffix("x")) else selector == version

    override fun compareSelectors(
        a: String,
        b: String,
    ) = compareSegments(upperBound(a), upperBound(b))

    override fun updateType(
        from: String,
        to: String,
    ) = when (segments(from).zip(segments(to)).indexOfFirst { (f, t) -> f != t }) {
        0 -> UpdateType.MAJOR
        1 -> UpdateType.MINOR
        2 -> UpdateType.PATCH
        else -> UpdateType.OTHER
    }

    private fun segments(version: String) = version.split('.').map { it.toInt() }

    // `1.2.x` allows everything below `1.3`. An exact version is its own bound.
    private fun upperBound(selector: String): List<Int> {
        if (!selector.endsWith(".x")) return segments(selector) + 0
        val prefix = segments(selector.removeSuffix(".x"))
        return prefix.dropLast(1) + (prefix.last() + 1)
    }

    private fun compareSegments(
        a: List<Int>,
        b: List<Int>,
    ): Int =
        a
            .zip(b)
            .map { (x, y) -> x.compareTo(y) }
            .firstOrNull { it != 0 } ?: a.size.compareTo(b.size)
}
