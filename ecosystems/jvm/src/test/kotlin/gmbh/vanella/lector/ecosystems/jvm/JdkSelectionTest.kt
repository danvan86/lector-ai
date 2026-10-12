package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.ToolError
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JdkSelectionTest {
    @TempDir
    lateinit var repo: Path

    private val installed = mapOf(17 to Path.of("/opt/java/17"), 21 to Path.of("/opt/java/21"), 25 to Path.of("/opt/java/25"))
    private val jdks = JdkSelection(installed)

    @Test
    fun `release 11 gets the smallest JDK there is, 17`() {
        pom(properties("maven.compiler.release" to "11"))

        assertEquals(Path.of("/opt/java/17"), jdks.javaHome(repo))
    }

    @Test
    fun `java_version 21 gets JDK 21`() {
        pom(properties("java.version" to "21"))

        assertEquals(Path.of("/opt/java/21"), jdks.javaHome(repo))
    }

    @Test
    fun `nothing declared gets JDK 21`() {
        pom("<project><modelVersion>4.0.0</modelVersion></project>")

        assertEquals(Path.of("/opt/java/21"), jdks.javaHome(repo))
    }

    @Test
    fun `release 26 is newer than every installed JDK, a tool error`() {
        pom(properties("maven.compiler.release" to "26"))

        val error = assertFailsWith<ToolError> { jdks.javaHome(repo) }

        assertEquals("The project needs Java 26, but only JDKs 17, 21, 25 are installed", error.message)
    }

    @Test
    fun `a release between two JDKs gets the next higher one`() {
        pom(properties("java.version" to "22"))

        assertEquals(Path.of("/opt/java/25"), jdks.javaHome(repo))
    }

    @Test
    fun `the release wins over java_version, and a property reference is resolved`() {
        pom(properties("java.version" to "25", "maven.compiler.release" to "\${java.version}"))
        assertEquals(Path.of("/opt/java/25"), jdks.javaHome(repo))

        pom(properties("java.version" to "17", "maven.compiler.release" to "21"))
        assertEquals(Path.of("/opt/java/21"), jdks.javaHome(repo))
    }

    @Test
    fun `the old 1_x notation counts as its minor version`() {
        pom(properties("java.version" to "1.8"))

        assertEquals(Path.of("/opt/java/17"), jdks.javaHome(repo))
    }

    @Test
    fun `a value that isn't a release, or a POM that doesn't parse, gets the default`() {
        pom(properties("maven.compiler.release" to "\${release.from.parent}"))
        assertEquals(Path.of("/opt/java/21"), jdks.javaHome(repo))

        pom("<project><properties>")
        assertEquals(Path.of("/opt/java/21"), jdks.javaHome(repo))
    }

    @Test
    fun `without a pom_xml the default applies`() {
        assertEquals(Path.of("/opt/java/21"), jdks.javaHome(repo))
    }

    @Test
    fun `without installed JDKs nothing is selected, and the environment's JAVA_HOME applies`() {
        pom(properties("maven.compiler.release" to "26"))

        assertNull(JdkSelection(emptyMap()).javaHome(repo))
    }

    private fun pom(xml: String) = repo.resolve("pom.xml").writeText(xml)

    private fun properties(vararg properties: Pair<String, String>) =
        "<project><properties>" + properties.joinToString("") { (name, value) -> "<$name>$value</$name>" } + "</properties></project>"
}
