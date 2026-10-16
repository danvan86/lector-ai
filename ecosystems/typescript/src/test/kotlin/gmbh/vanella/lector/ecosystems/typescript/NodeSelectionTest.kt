package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.ToolError
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NodeSelectionTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var nodes: Path

    private val installed by lazy { mapOf(22 to node("22.23.3"), 24 to node("24.21.0")) }
    private val selection by lazy { NodeSelection(installed) }

    @Test
    fun `nvmrc and node-version name a version, a major, lts, or an LTS codename`() {
        for ((declared, major) in listOf("22" to 22, "v24" to 24, "22.11.0" to 22, "lts/*" to 24, "lts/jod" to 22, "lts/Krypton" to 24)) {
            repo.resolve(".nvmrc").writeText("$declared\n")

            assertEquals(installed.getValue(major), selection.home(repo, engines = ">=26"), declared)
        }
        repo.resolve(".nvmrc").toFile().delete()
        repo.resolve(".node-version").writeText("# comment\n\n22\n")

        assertEquals(installed.getValue(22), selection.home(repo, engines = null))
    }

    @Test
    fun `without a version file, the highest installed Node that engines allows`() {
        assertEquals(installed.getValue(24), selection.home(repo, engines = ">=18"))
        assertEquals(installed.getValue(22), selection.home(repo, engines = "^22.12 || ^20"))
        assertEquals(installed.getValue(22), selection.home(repo, engines = ">=22.23.3 <24"))
    }

    @Test
    fun `without a version file or engines, the newest LTS`() {
        assertEquals(installed.getValue(24), selection.home(repo, engines = null))
        assertEquals(installed.getValue(24), NodeSelection(installed + (25 to node("25.9.0"))).home(repo, engines = null))
        assertEquals(
            installed.getValue(22),
            NodeSelection(mapOf(21 to node("21.7.3"), 22 to installed.getValue(22))).home(repo, engines = null),
        )
        assertEquals(
            installed.getValue(24),
            NodeSelection(mapOf(23 to node("23.0.0"), 24 to installed.getValue(24))).home(repo, engines = null),
        )
    }

    @Test
    fun `a version that isn't installed is a tool error`() {
        repo.resolve(".nvmrc").writeText("20\n")
        assertEquals(
            "The project asks for Node 20 (.nvmrc), but only Node 22, 24 are installed.",
            assertFailsWith<ToolError> { selection.home(repo, engines = null) }.message,
        )

        repo.resolve(".nvmrc").writeText("lts/iron\n")
        assertEquals(
            "The project asks for Node lts/iron (.nvmrc), but only Node 22, 24 are installed.",
            assertFailsWith<ToolError> { selection.home(repo, engines = null) }.message,
        )

        repo.resolve(".nvmrc").toFile().delete()
        assertEquals(
            "The project asks for Node >=26 (engines.node in package.json), but only Node 22, 24 are installed.",
            assertFailsWith<ToolError> { selection.home(repo, engines = ">=26") }.message,
        )
    }

    @Test
    fun `a version file lector-ai can't read is a tool error`() {
        repo.resolve(".nvmrc").writeText("node\n")

        assertEquals(
            "Can't read the Node version node in .nvmrc. lector-ai reads a version, a major, lts/*, or an LTS codename.",
            assertFailsWith<ToolError> { selection.home(repo, engines = null) }.message,
        )
        repo.resolve(".nvmrc").writeText("99999999999\n")
        assertFailsWith<ToolError> { selection.home(repo, engines = null) }
    }

    @Test
    fun `without installed Nodes, nothing is picked and the PATH's node runs`() {
        repo.resolve(".nvmrc").writeText("20\n")

        assertNull(NodeSelection(emptyMap()).home(repo, engines = ">=26"))
    }

    @Test
    fun `engines lector-ai can't read, or a Node without its version header, is a tool error`() {
        assertEquals(
            "Can't read engines.node latest in package.json. lector-ai reads npm ranges, e.g. >=22.",
            assertFailsWith<ToolError> { selection.home(repo, engines = "latest") }.message,
        )
        val broken = nodes.resolve("broken")

        assertEquals(
            "Can't read the version of the Node in $broken: ${broken.resolve("include/node/node_version.h")} is missing or incomplete.",
            assertFailsWith<ToolError> { NodeSelection(mapOf(22 to broken)).home(repo, engines = ">=22") }.message,
        )
    }

    /** A Node installation with only the header its version is read from. */
    private fun node(version: String): Path {
        val (major, minor, patch) = version.split('.')
        val home = nodes.resolve(version)
        home.resolve("include/node").createDirectories().resolve("node_version.h").writeText(
            """
            #ifndef SRC_NODE_VERSION_H_
            #define SRC_NODE_VERSION_H_

            #define NODE_MAJOR_VERSION $major
            #define NODE_MINOR_VERSION $minor
            #define NODE_PATCH_VERSION $patch
            """.trimIndent(),
        )
        return home
    }

    @Test
    fun `a version file with only comments names no version, and an unknown codename can't be read`() {
        repo.resolve(".nvmrc").writeText("# set by the platform team\n")
        assertEquals(installed.getValue(22), selection.home(repo, engines = "22"))

        repo.resolve(".nvmrc").writeText("lts/unknown\n")
        assertFailsWith<ToolError> { selection.home(repo, engines = null) }
    }

    @Test
    fun `with only odd majors installed, the newest LTS is the highest`() {
        assertEquals(nodes.resolve("25.9.0"), NodeSelection(mapOf(25 to node("25.9.0"))).home(repo, engines = null))
    }

    @Test
    fun `the installed Nodes come from LECTOR_NODE_ variables`() {
        assertEquals(
            mapOf(22 to Path.of("/opt/node/22"), 24 to Path.of("/opt/node/24")),
            installedNodes(
                mapOf(
                    "LECTOR_NODE_22" to "/opt/node/22",
                    "LECTOR_NODE_24" to "/opt/node/24",
                    "LECTOR_NODE_X" to "/x",
                    "PATH" to "/bin",
                ),
            ),
        )
    }
}
