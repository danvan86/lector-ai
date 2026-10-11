package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClusterTest {
    private val adapter = KeyedAdapter()

    @Test
    fun `diagnostics are grouped by the adapter's cluster key, and without a key by file, in order of appearance`() {
        val a1 = compileError("A.java", key = "symbol:a")
        val a2 = compileError("B.java", key = "symbol:a")
        val c1 = compileError("C.java")
        val c2 = compileError("C.java")
        val noLocation = Diagnostic(null, null, null, null, "build failed", Severity.ERROR, "maven")

        val clusters = clusters(listOf(c1, a1, noLocation, a2, c2), adapter)

        assertEquals(
            listOf(Cluster("(no location)", listOf(noLocation)), Cluster("C.java", listOf(c1, c2)), Cluster("symbol:a", listOf(a1, a2))),
            clusters,
        )
    }

    @Test
    fun `clusters with build-file errors come first, then the largest cluster, and a test failure is no build error`() {
        val small = listOf(compileError("S.java"))
        val large = List(3) { compileError("L$it.java", key = "symbol:large") }
        val pom = listOf(compileError("pom.xml"))
        val missingArtifact = List(2) { Diagnostic(null, null, null, "artifact:lib", "Could not find artifact", Severity.ERROR, "maven") }
        val testFailures = List(4) { Diagnostic(null, null, null, "test", "expected 1", Severity.ERROR, "surefire", "ATest#a$it") }

        val clusters = clusters(small + large + pom + missingArtifact + testFailures, adapter)

        assertEquals(listOf("artifact:lib", "pom.xml", "test", "symbol:large", "S.java"), clusters.map { it.key })
    }

    @Test
    fun `progress is counted per stage, from build errors to test failures`() {
        val diagnostics =
            listOf(
                Diagnostic(null, null, null, null, "build failed", Severity.ERROR, "maven"),
                compileError("A.java"),
                compileError("B.java"),
                Diagnostic("ATest.java", 3, null, null, "expected 1", Severity.ERROR, "surefire", "ATest#a"),
                Diagnostic(null, null, null, null, "expected 2", Severity.ERROR, "surefire", "BTest#b"),
            )

        assertEquals(Progress(1, 2, 2), Progress.of(diagnostics))
        assertEquals("1 build errors, 2 compile errors, 1 test failures", Progress(1, 2, 1).toString())
    }

    @Test
    fun `progress is compared stage by stage`() {
        assertTrue(Progress(0, 50, 0) < Progress(1, 0, 0), "a fixed root cause counts, even if it unmasks compile errors")
        assertTrue(Progress(0, 1, 0) > Progress(0, 0, 3), "a new compile error never counts while fixing tests")
        assertTrue(Progress(0, 0, 2) < Progress(0, 0, 3))
        assertEquals(0, Progress(0, 1, 2).compareTo(Progress(0, 1, 2)))
    }

    private fun compileError(
        file: String,
        key: String? = null,
    ) = Diagnostic(file, 1, 1, key, "cannot find symbol", Severity.ERROR, "javac")

    /** Uses the diagnostic's `code` as its cluster key. `pom.xml` is the build file. */
    private class KeyedAdapter : EcosystemAdapter {
        override val ecosystem = "test"
        override val languageSupport = LanguageSupport { _, _ -> null }

        override fun isEditable(file: String) = true

        override fun testDisablingConfig(
            file: String,
            content: String,
            line: Int,
        ): String? = null

        override val versionScheme = DottedVersions
        override val recipeRunner = null
        override val resolvedVersions = null

        override fun detect(repo: Path) = true

        override fun verify(repo: Path) = emptyList<Diagnostic>()

        override fun runTests(
            repo: Path,
            tests: List<Diagnostic>,
        ) = emptyList<Diagnostic>()

        override fun ground(
            repo: Path,
            change: DependencyChange,
            diagnostics: List<Diagnostic>,
        ) = ""

        override fun clusterKey(diagnostic: Diagnostic) = diagnostic.code

        override fun isBuildFile(file: String) = file == "pom.xml"
    }
}
