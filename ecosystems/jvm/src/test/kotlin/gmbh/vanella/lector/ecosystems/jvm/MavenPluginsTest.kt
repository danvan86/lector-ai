package gmbh.vanella.lector.ecosystems.jvm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MavenPluginsTest {
    @Test
    fun `the pinned plugins come from the version catalog`() {
        val plugins = MavenPlugins.load()

        assertTrue(plugins.rewrite.matches(Regex("""org\.openrewrite\.maven:rewrite-maven-plugin:\d+\.\d+\.\d+""")), plugins.rewrite)
        assertTrue(plugins.help.matches(Regex("""org\.apache\.maven\.plugins:maven-help-plugin:\d+\.\d+\.\d+""")), plugins.help)
    }

    @Test
    fun `a missing resource is an error that names it`() {
        val error = assertFailsWith<IllegalStateException> { MavenPlugins.load("no-such.properties") }

        assertEquals("no-such.properties is missing from the classpath", error.message)
    }

    @Test
    fun `a missing plugin is an error that names it`() {
        val error = assertFailsWith<IllegalStateException> { MavenPlugins.load("no-help-plugin.properties") }

        assertEquals("no-help-plugin.properties has no help plugin", error.message)
    }
}
