package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.UpdateType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemverTest {
    @Test
    fun `orders versions by semver 2_0, pre-releases before their release`() {
        val ordered = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0")
        val rest = listOf("1.0.1", "1.1.0", "2.0.0", "10.0.0")

        assertEquals(ordered + rest, (ordered + rest).shuffled().sortedWith(SemverScheme::compare))
    }

    @Test
    fun `build metadata, a leading v, and missing numbers don't change the order`() {
        assertEquals(0, SemverScheme.compare("1.0.0+build.5", "1.0.0"))
        assertTrue(SemverScheme.compare("1.0.0", "1.0.0-rc.1") > 0)
        assertTrue(SemverScheme.compare("1.0.0-rc.1", "1.0.0") < 0)
        assertEquals(0, SemverScheme.compare("v2.1.0", "2.1.0"))
        assertEquals(0, SemverScheme.compare("2", "2.0.0"))
        assertEquals(0, SemverScheme.compare("^2.1", "2.1.0"), "a range is read as its version")
    }

    @Test
    fun `text that isn't a version sorts by its characters, after every version`() {
        assertTrue(SemverScheme.compare("latest", "1.0.0") > 0)
        assertTrue(SemverScheme.compare("1.0.0", "next") < 0)
        assertTrue(SemverScheme.compare("latest", "next") < 0)
    }

    @Test
    fun `the update type is the first number that changed`() {
        assertEquals(UpdateType.MAJOR, SemverScheme.updateType("1.4.2", "2.0.0"))
        assertEquals(UpdateType.MINOR, SemverScheme.updateType("1.4.2", "1.5.0"))
        assertEquals(UpdateType.PATCH, SemverScheme.updateType("1.4.2", "1.4.3"))
        assertEquals(UpdateType.MAJOR, SemverScheme.updateType("^1.4.2", "^2.0.0"))
        assertEquals(UpdateType.OTHER, SemverScheme.updateType("1.4.2-rc.1", "1.4.2"))
        assertEquals(UpdateType.OTHER, SemverScheme.updateType("latest", "1.4.2"))
    }

    @Test
    fun `contains reads npm ranges`() {
        val cases =
            mapOf(
                "1.2.3" to listOf("1.2.3"),
                "=1.2.3" to listOf("1.2.3"),
                "v1.2.3" to listOf("1.2.3"),
                ">=18" to listOf("18.0.0", "22.23.3", "24.21.0"),
                ">=20.11 <23" to listOf("20.11.0", "22.23.3"),
                ">20 <=22.5.0" to listOf("22.5.0", "21.0.0"),
                "^22 || ^24" to listOf("22.0.0", "24.21.0"),
                "^0.2.3" to listOf("0.2.3", "0.2.9"),
                "^0.0.3" to listOf("0.0.3"),
                "~22.1" to listOf("22.1.0", "22.1.9"),
                "~1" to listOf("1.0.0", "1.9.9"),
                "22" to listOf("22.0.0", "22.23.3"),
                "22.x" to listOf("22.23.3"),
                "1.2.*" to listOf("1.2.0", "1.2.7"),
                "*" to listOf("0.0.1", "24.21.0"),
                "" to listOf("24.21.0"),
                "x" to listOf("24.21.0"),
                "20.1.0 - 22.2" to listOf("20.1.0", "22.2.9"),
            )
        val misses =
            mapOf(
                "1.2.3" to listOf("1.2.4"),
                ">=18" to listOf("16.20.0"),
                ">=20.11 <23" to listOf("20.10.0", "23.0.0", "24.21.0"),
                ">20 <=22.5.0" to listOf("20.9.0", "22.5.1"),
                "^22 || ^24" to listOf("23.0.0", "26.0.0"),
                "^0.2.3" to listOf("0.3.0", "0.2.2"),
                "^0.0.3" to listOf("0.0.4"),
                "~22.1" to listOf("22.2.0"),
                "22" to listOf("24.21.0"),
                "20.1.0 - 22.2" to listOf("22.3.0", "20.0.9"),
                "^1.0.0" to listOf("1.2.0-rc.1"),
                "not a range" to listOf("1.0.0"),
                ">=" to listOf("1.0.0"),
            )

        cases.forEach { (range, versions) -> versions.forEach { assertTrue(SemverScheme.contains(range, it), "$range contains $it") } }
        misses.forEach { (range, versions) ->
            versions.forEach { assertFalse(SemverScheme.contains(range, it), "$range doesn't contain $it") }
        }
    }

    @Test
    fun `selectors sort by the highest version they allow, a bound below a version before that version`() {
        val ordered = listOf("1.2.3", "1.2.x", "1.3.0", "^1.3.0", "2.0.1", "~2.0.0", "<3", "3.0.0", ">=3", "not a range")

        assertEquals(ordered, ordered.reversed().sortedWith(SemverScheme::compareSelectors))
    }

    @Test
    fun `more of npm's range syntax`() {
        val cases =
            mapOf(
                ">1.2.3" to listOf("1.2.4"),
                ">1.2" to listOf("1.3.0"),
                "<=1.2" to listOf("1.2.9"),
                "<=1.2.3" to listOf("1.2.3"),
                "<1" to listOf("0.9.0"),
                "~>1.2" to listOf("1.2.5"),
                "~*" to listOf("3.0.0"),
                "^*" to listOf("3.0.0"),
                ">= 1.2 < 2" to listOf("1.5.0"),
                ">=1.2.0-rc.1" to listOf("1.2.0-rc.2"),
            )
        val misses =
            mapOf(
                ">1.2.3" to listOf("1.2.3"),
                ">1.2" to listOf("1.2.9"),
                ">*" to listOf("1.0.0"),
                "<=1.2" to listOf("1.3.0"),
                "<1" to listOf("1.0.0"),
                ">=1.2.0-rc.1" to listOf("1.3.0-rc.1"),
                "1.0 - nope" to listOf("1.0.0"),
                "nope - 1.0" to listOf("1.0.0"),
                "1.x" to listOf("latest"),
            )

        cases.forEach { (range, versions) -> versions.forEach { assertTrue(SemverScheme.contains(range, it), "$range contains $it") } }
        misses.forEach { (range, versions) ->
            versions.forEach { assertFalse(SemverScheme.contains(range, it), "$range doesn't contain $it") }
        }
    }

    @Test
    fun `pre-release identifiers compare numbers below words, and anything that isn't a selector last`() {
        assertTrue(SemverScheme.compare("1.0.0-beta", "1.0.0-1") > 0)
        assertTrue(SemverScheme.compare("1.0.0-1", "1.0.0-beta") < 0)
        assertEquals(UpdateType.OTHER, SemverScheme.updateType("1.4.2", "latest"))
        assertTrue(SemverScheme.compareSelectors("bad", "worse") < 0)
        assertTrue(SemverScheme.compareSelectors("bad", "1.0.0") > 0)
        assertTrue(SemverScheme.compareSelectors("1.0.0", "bad") < 0)
        assertEquals(0, SemverScheme.compareSelectors(">=1", ">=2"))
        assertTrue(SemverScheme.compareSelectors(">=1", "1.0.0") > 0)
    }

    @Test
    fun `numbers longer than an Int are no version, and long pre-release numbers still compare as numbers`() {
        assertEquals(null, Semver.parse("99999999999.0.0"))
        assertTrue(SemverScheme.compare("1.0.0-dev.9999999999", "1.0.0-dev.10000000000") < 0)
        assertTrue(SemverScheme.compare("1.0.0-dev.10000000000", "1.0.0-dev.10000000001") < 0)
        assertEquals(0, SemverScheme.compare("1.0.0-dev.10000000000", "1.0.0-dev.10000000000"))
    }
}
