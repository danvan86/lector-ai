package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** Cluster keys on the diagnostics parsed from captured Maven output (see `fixtures/README.md`). */
class ClusterKeysTest {
    private val adapter = MavenAdapter()

    @Test
    fun `javac's missing symbols are keyed by the symbol's name`() {
        assertEquals(listOf("symbol:greetPolitely", "symbol:Strng"), keys("javac-errors.log"))
    }

    @Test
    fun `javac's missing package is keyed by the package, and a symbol from it by the symbol`() {
        assertEquals(listOf("package:org.apache.commons.lang4", "symbol:StringUtils"), keys("javac-missing-package.log"))
    }

    @Test
    fun `kotlinc's unresolved references are keyed by the symbol's name, in Kotlin 2 and 1_9`() {
        assertEquals(listOf("symbol:capitalized", null), keys("kotlinc-errors.log"))
        assertEquals(listOf("symbol:capitalized", null), keys("kotlinc-1.9-errors.log"))
    }

    @Test
    fun `a missing dependency or parent is keyed by the artifact, without type and version`() {
        assertEquals(listOf("artifact:org.apache.commons:commons-lang3"), keys("dependency-resolution.log"))
        assertEquals(listOf("artifact:org.springframework.boot:spring-boot-starter-parent"), keys("missing-parent.log"))
    }

    @Test
    fun `other problems have no key`() {
        val cannotFindSymbolAlone = Diagnostic("App.java", 1, 1, null, "cannot find symbol", Severity.ERROR, "javac")
        val testFailure = Diagnostic("AppTest.java", 1, null, "AssertionError", "expected 1", Severity.ERROR, "surefire", "AppTest#runs")

        assertEquals(null, adapter.clusterKey(cannotFindSymbolAlone))
        assertEquals(null, adapter.clusterKey(testFailure))
        assertEquals(
            null,
            adapter.clusterKey(cannotFindSymbolAlone.copy(message = "incompatible types: int cannot be converted to String")),
        )
        assertEquals(null, adapter.clusterKey(Diagnostic(null, null, null, null, "Failed to execute goal", Severity.ERROR, "maven")))
    }

    @Test
    fun `pom_xml is a build file, in every module`() {
        assertEquals(true, adapter.isBuildFile("pom.xml"))
        assertEquals(true, adapter.isBuildFile("app/pom.xml"))
        assertEquals(false, adapter.isBuildFile("src/main/resources/pom.xml.template"))
        assertEquals(false, adapter.isBuildFile("src/main/java/App.java"))
    }

    private fun keys(log: String): List<String?> {
        val output = requireNotNull(javaClass.getResource("/maven-output/$log")).readText()
        return MavenConsole.parse(output, Path.of("/work/repo")).map(adapter::clusterKey)
    }
}
