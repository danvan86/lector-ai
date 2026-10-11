package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.UpdateType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MavenVersionSchemeTest {
    private val scheme = MavenVersionScheme

    @Test
    fun `orders versions the Maven way`() {
        assertTrue(scheme.compare("3.2.0-RC1", "3.2.0") < 0)
        assertTrue(scheme.compare("3.10.0", "3.9.9") > 0)
        assertTrue(scheme.compare("3.4.1", "3.4.1.0") == 0)
        assertTrue(scheme.compare("1.0-SNAPSHOT", "1.0") < 0)
    }

    @Test
    fun `an X-range contains the versions that start with its prefix`() {
        assertTrue(scheme.contains("3.4.x", "3.4.1"))
        assertTrue(scheme.contains("3.4.x", "3.4.0-RC1"))
        assertTrue(scheme.contains("3.4.x", "3.4"))
        assertTrue(scheme.contains("3.x", "3.4.1"))
        assertTrue(scheme.contains("3.4.X", "3.4.1"))
        assertTrue(scheme.contains("3.4.*", "3.4.1"))
        assertFalse(scheme.contains("3.4.x", "3.5.0"))
        assertFalse(scheme.contains("3.4.x", "3.5.0-RC1"))
        assertFalse(scheme.contains("3.4.x", "3"))
        assertFalse(scheme.contains("3.x", "13.0.0"))
    }

    @Test
    fun `an exact version contains only itself`() {
        assertTrue(scheme.contains("3.4.1", "3.4.1"))
        assertTrue(scheme.contains("3.4", "3.4.0"))
        assertFalse(scheme.contains("3.4.1", "3.4.2"))
    }

    @Test
    fun `other selectors contain nothing`() {
        assertFalse(scheme.contains("latest.release", "3.4.1"))
        assertFalse(scheme.contains("[3.0,4.0)", "3.4.1"))
        assertFalse(scheme.contains("x", "3.4.1"))
    }

    @Test
    fun `selectors are ordered by the highest version they allow`() {
        assertTrue(scheme.compareSelectors("3.3.x", "3.4.x") < 0)
        assertTrue(scheme.compareSelectors("3.4.x", "3.x") < 0)
        assertTrue(scheme.compareSelectors("3.4.1", "3.4.x") < 0)
        assertTrue(scheme.compareSelectors("3.4.x", "3.4.1") > 0)
        assertTrue(scheme.compareSelectors("3.4.x", "3.4.*") == 0)
        assertTrue(scheme.compareSelectors("3.4.2", "3.4.1") > 0)
    }

    @Test
    fun `an X-range stays below the next version of its prefix`() {
        assertTrue(scheme.compareSelectors("3.4.x", "3.5") < 0)
        assertTrue(scheme.compareSelectors("3.5.0", "3.4.x") > 0)
    }

    @Test
    fun `the update type is the first part of the version that changed`() {
        assertEquals(UpdateType.MAJOR, scheme.updateType("2.7.18", "3.0.0"))
        assertEquals(UpdateType.MINOR, scheme.updateType("3.3.5", "3.4.1"))
        assertEquals(UpdateType.PATCH, scheme.updateType("3.4.0", "3.4.1"))
        assertEquals(UpdateType.PATCH, scheme.updateType("3.4", "3.4.1"))
        assertEquals(UpdateType.MINOR, scheme.updateType("3.4.1", "3.5"))
        assertEquals(UpdateType.OTHER, scheme.updateType("3.4.1", "3.4.1.1"))
        assertEquals(UpdateType.OTHER, scheme.updateType("3.4.1-RC1", "3.4.1"))
        assertEquals(UpdateType.OTHER, scheme.updateType("Hoxton.SR1", "Hoxton.SR2"))
        assertEquals(UpdateType.OTHER, scheme.updateType("Hoxton.SR1", "2020.0.1"))
        assertEquals(UpdateType.OTHER, scheme.updateType("2020.0.1", "Hoxton.SR1"))
    }
}
