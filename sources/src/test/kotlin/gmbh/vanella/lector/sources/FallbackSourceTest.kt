package gmbh.vanella.lector.sources

import gmbh.vanella.lector.core.BranchPolicy
import gmbh.vanella.lector.core.PackageUpdate
import gmbh.vanella.lector.core.PullRequest
import gmbh.vanella.lector.core.SourceContext
import gmbh.vanella.lector.core.ToolError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The fallback against resolved versions that a fake context hands out for the base and the head. */
class FallbackSourceTest {
    private val source = FallbackSource("lector")

    @Test
    fun `recognizes a PR by the opt-in label, whoever opened it`() {
        assertTrue(source.recognizes(pr(labels = setOf("dependencies", "lector"))))
        assertTrue(source.recognizes(pr(labels = setOf("lector")).copy(author = "dependabot[bot]")))
        assertFalse(source.recognizes(pr(labels = setOf("dependencies"))))
        assertFalse(FallbackSource("upgrade").recognizes(pr(labels = setOf("lector"))))
    }

    @Test
    fun `is called fallback and fixes in a companion PR`() {
        assertEquals("fallback", source.name)
        assertEquals(BranchPolicy.COMPANION, source.branchPolicy)
    }

    @Test
    fun `every package whose resolved version differs is a change, transitive, added, and removed ones included, sorted`() {
        val context =
            Versions(
                base =
                    mapOf(
                        "org.example:app-parent" to "1.0",
                        "org.example:transitive" to "2.0",
                        "org.example:gone" to "3.0",
                        "org.example:same" to "4.0",
                    ),
                head =
                    mapOf(
                        "org.example:app-parent" to "1.1",
                        "org.example:transitive" to "2.1",
                        "org.example:new" to "5.0",
                        "org.example:same" to "4.0",
                    ),
            )

        val update = source.read(pr(), context)

        assertEquals(
            listOf(
                PackageUpdate("maven", "org.example:app-parent", "1.0", "1.1"),
                PackageUpdate("maven", "org.example:gone", "3.0", ""),
                PackageUpdate("maven", "org.example:new", "", "5.0"),
                PackageUpdate("maven", "org.example:transitive", "2.0", "2.1"),
            ),
            update.updates,
        )
        assertNull(update.releaseNotes)
    }

    @Test
    fun `unreadable versions on either side are a tool error that says which side`() {
        val base = assertFailsWith<ToolError> { source.read(pr(), Versions(base = null, head = emptyMap())) }
        val head = assertFailsWith<ToolError> { source.read(pr(), Versions(base = emptyMap(), head = null)) }

        assertEquals(
            "The resolved versions of the base commit can't be read, so lector-ai can't tell what the PR changes: mvn failed",
            base.message,
        )
        assertEquals(
            "The resolved versions of the PR's head can't be read, so lector-ai can't tell what the PR changes: mvn failed",
            head.message,
        )
    }

    /** Resolved versions of the base and the head; null fails like a build tool that can't read them. */
    private class Versions(
        private val base: Map<String, String>?,
        private val head: Map<String, String>?,
    ) : SourceContext {
        override val ecosystem = "maven"

        override fun baseVersions() = base ?: throw ToolError("mvn failed")

        override fun headVersions() = head ?: throw ToolError("mvn failed")
    }

    private fun pr(labels: Set<String> = setOf("lector")) =
        PullRequest(7, "alice", "bump-spring", "head", "acme/app", "acme/app", "base", "Bump Spring Boot", labels)
}
