package gmbh.vanella.lector.recipes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Checks the index the build generated from `rewrite-recipe-bom`, as it ships. */
class GeneratedIndexTest {
    private val index = BundledRecipeIndex.load()
    private val report =
        assertNotNull(javaClass.getResourceAsStream(REPORT_RESOURCE), "$REPORT_RESOURCE is missing").use {
            it.readBytes().decodeToString()
        }

    @Test
    fun `contains the Spring Boot 3_4 upgrade for the starter parent, as a direct leaf`() {
        val leaves =
            index.entries.filter {
                it.recipe == "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4" &&
                    it.packagePattern == "org.springframework.boot:spring-boot-starter-parent"
            }
        val entry = leaves.single { it.selector == "3.4.x" }

        // The 3.3 and older upgrades it includes are deeper leaves of the same recipe.
        assertTrue(leaves.any { it.selector == "3.3.x" && it.depth == 2 }, leaves.toString())
        assertEquals("maven", entry.ecosystem)
        assertEquals(1, entry.depth)
        assertTrue(entry.artifact.startsWith("org.openrewrite.recipe:rewrite-spring:"), entry.artifact)
        assertEquals("Moderne Source Available License", entry.license)
    }

    @Test
    fun `holds only exact versions and X-ranges`() {
        val unsupported = index.entries.filter { it.selector.startsWith("latest.") || it.selector.startsWith("[") }

        assertEquals(emptyList(), unsupported)
    }

    @Test
    fun `the report names the license of rewrite-spring and counts the skipped latest_release leaves`() {
        assertContains(report, Regex("""\| org\.openrewrite\.recipe:rewrite-spring:[^ ]+ \| Moderne Source Available License \| \d+ \|"""))
        assertContains(report, Regex("""\| LatestRelease \| \d+ \|"""))
    }

    private companion object {
        const val REPORT_RESOURCE = "recipe-index-report.md"
    }
}
