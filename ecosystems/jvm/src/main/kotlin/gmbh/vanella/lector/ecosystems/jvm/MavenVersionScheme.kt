package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.UpdateType
import gmbh.vanella.lector.core.VersionScheme
import org.apache.maven.artifact.versioning.ComparableVersion

private val X_RANGE = Regex("""(\d+(?:\.\d+)*)\.[xX*]""")

// The update type of a change in the first, second, or third number of the version.
private val UPDATE_TYPES = listOf(UpdateType.MAJOR, UpdateType.MINOR, UpdateType.PATCH)

/**
 * Maven versions, ordered with Maven's own `ComparableVersion` (`3.2.0-RC1 < 3.2.0`). Selectors are exact versions or
 * X-ranges as OpenRewrite writes them (`3.4.x`, `3.x`). An X-range contains the versions whose leading numbers are its
 * prefix. Any other selector contains nothing.
 */
object MavenVersionScheme : VersionScheme {
    override fun compare(
        a: String,
        b: String,
    ) = ComparableVersion(a).compareTo(ComparableVersion(b))

    override fun contains(
        selector: String,
        version: String,
    ): Boolean {
        val prefix = xRangePrefix(selector) ?: return compare(selector, version) == 0
        return parts(version).take(prefix.size) == prefix
    }

    override fun compareSelectors(
        a: String,
        b: String,
    ): Int {
        val (boundA, exclusiveA) = upperBound(a)
        val (boundB, exclusiveB) = upperBound(b)
        val byBound = boundA.compareTo(boundB)
        // At the same bound, `below 3.5` (an X-range) sorts before `3.5` (an exact version).
        return if (byBound != 0) byBound else exclusiveB.compareTo(exclusiveA)
    }

    override fun updateType(
        from: String,
        to: String,
    ): UpdateType {
        val before = parts(from)
        val after = parts(to)
        if (before.isEmpty() || after.isEmpty()) return UpdateType.OTHER
        // A missing number counts as 0: `3.4` → `3.4.1` is a patch.
        val padding = List(UPDATE_TYPES.size) { 0 }
        val changed = UPDATE_TYPES.indices.firstOrNull { (before + padding)[it] != (after + padding)[it] } ?: return UpdateType.OTHER
        return UPDATE_TYPES[changed]
    }

    private fun xRangePrefix(selector: String): List<Int>? {
        val match = X_RANGE.matchEntire(selector) ?: return null
        return match.groupValues[1].split('.').map { it.toInt() }
    }

    /** The leading numbers of a version: `3.4.1-RC1` → `[3, 4, 1]`. */
    private fun parts(version: String) =
        version
            .split('.', '-')
            .map { it.toIntOrNull() }
            .takeWhile { it != null }
            .filterNotNull()

    /** The highest version a selector allows, and whether that bound itself is excluded (`3.4.x` → below `3.5`). */
    private fun upperBound(selector: String): Pair<ComparableVersion, Boolean> {
        val prefix = xRangePrefix(selector) ?: return ComparableVersion(selector) to false
        val next = prefix.dropLast(1) + (prefix.last() + 1)
        return ComparableVersion(next.joinToString(".")) to true
    }
}
