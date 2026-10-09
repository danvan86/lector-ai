package gmbh.vanella.lector.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VersionTest {
    @Test
    fun `version comes from the Gradle build`() {
        assertEquals(System.getProperty("lector.version"), appVersion())
    }

    @Test
    fun `missing version resource fails with a clear message`() {
        val error = assertFailsWith<IllegalArgumentException> { appVersion("missing.properties") }

        assertEquals("missing.properties is missing from the classpath", error.message)
    }
}
