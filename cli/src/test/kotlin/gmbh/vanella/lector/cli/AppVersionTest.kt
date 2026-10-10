package gmbh.vanella.lector.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppVersionTest {
    @Test
    fun `version comes from the Gradle build`() {
        assertEquals(System.getProperty("lector.version"), AppVersion.read())
    }

    @Test
    fun `missing version resource fails with a clear message`() {
        val error = assertFailsWith<IllegalArgumentException> { AppVersion.read("missing.properties") }

        assertEquals("missing.properties is missing from the classpath", error.message)
    }

    @Test
    fun `version resource without a version fails with a clear message`() {
        val error = assertFailsWith<IllegalArgumentException> { AppVersion.read("no-version.properties") }

        assertEquals("no-version.properties has no version property", error.message)
    }
}
