package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.LockfileUpdate
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.ProcessTimedOut
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.UpdateType
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** The adapter as the core sees it: what it speaks for, detection, and how it finds and runs things. */
class TypeScriptAdapterTest {
    @TempDir
    lateinit var dir: Path

    private val repo by lazy { dir.resolve("repo") }
    private val commands = mutableListOf<List<String>>()

    @Test
    fun `speaks for npm with semver, and has no recipe runner (AC31)`() {
        val adapter = adapter()

        assertEquals("npm", adapter.ecosystem)
        assertEquals(SemverScheme, adapter.versionScheme)
        assertNull(adapter.recipeRunner)
        assertEquals(TypeScriptLanguageSupport, adapter.languageSupport)
    }

    @Test
    fun `detects a repository with package_json and tsconfig_json at the root (AC4)`() {
        fixture("npm-jest")
        assertTrue(adapter().detect(repo))

        repo.resolve("tsconfig.json").toFile().delete()
        assertFalse(adapter().detect(repo))
    }

    @Test
    fun `detection fails for a repository it can't build, and for a Node the image doesn't have (AC6, AC9)`() {
        fixture("npm-jest")
        repo.resolve("yarn.lock").writeText("# yarn lockfile v1\n")
        assertEquals(
            "$repo has more than one lockfile (package-lock.json, yarn.lock). lector-ai doesn't pick a package manager: remove all but one.",
            assertFailsWith<ToolError> { adapter().detect(repo) }.message,
        )

        repo.resolve("yarn.lock").toFile().delete()
        repo.resolve(".nvmrc").writeText("20\n")
        assertEquals(
            "The project asks for Node 20 (.nvmrc), but only Node 22, 24 are installed.",
            assertFailsWith<ToolError> { adapter(nodes = mapOf(22 to node("22"), 24 to node("24"))).detect(repo) }.message,
        )
    }

    @Test
    fun `reads resolved versions from the lockfile`() {
        val root = requireNotNull(javaClass.getResource("/lockfiles/pnpm")).toURI().toPath()

        assertEquals(mapOf("is-number" to "7.0.0", "is-odd" to "3.0.1"), adapter().resolvedVersions.read(root))
    }

    @Test
    fun `verify, runTests, testsRun, and updateLockfiles run through the package manager`() {
        fixture("yarn-classic")
        val adapter = adapter()

        assertEquals(emptyList(), adapter.verify(repo, 1.minutes))
        assertNull(adapter.testsRun(repo))
        assertEquals(emptyList(), adapter.runTests(repo, emptyList(), 1.minutes))
        assertEquals(LockfileUpdate.Updated(emptyList()), adapter.updateLockfiles(repo, 1.minutes))
        assertEquals(listOf("yarn", "install"), commands.last())
        adapter.close()
    }

    @Test
    fun `cluster keys, build files, the editable scope, and the blocklist are the TypeScript ones`() {
        val adapter = adapter()
        val error = Diagnostic("src/a.ts", 1, 1, "TS2339", "Property 'b' does not exist on type 'A'.", Severity.ERROR, "tsc")

        assertEquals("property:b", adapter.clusterKey(error))
        assertTrue(adapter.isBuildFile("tsconfig.json"))
        assertFalse(adapter.isEditable("yarn.lock"))
        assertEquals("`noCheck` turns the type check off", adapter.testDisablingConfig("tsconfig.json", "{\"noCheck\": true}", 1))
    }

    @Test
    fun `grounding finds the package in the nearest node_modules of the failing file`() {
        fixture("pnpm-workspace")
        pkg(repo.resolve("node_modules/dequal"), "export declare function dequal(): 1;\n")
        pkg(repo.resolve("packages/app/node_modules/dequal"), "export declare function dequal(): 2;\n")
        repo.resolve("packages/app/src/same.ts").writeText("import { dequal } from \"dequal\";\n")
        repo.resolve("packages/lib/src/same.ts").writeText("import { dequal } from \"dequal\";\n")

        assertEquals("export declare function dequal(): 2;", ground("packages/app/src/same.ts").lines().last())
        assertEquals("export declare function dequal(): 1;", ground("packages/lib/src/same.ts").lines().last())
        assertEquals("", ground("packages/app/src/welcome.ts"), "the file doesn't import it")
    }

    @Test
    fun `under Plug'n'Play, grounding asks Yarn where the package is, once`() {
        fixture("yarn-berry-pnp")
        repo.resolve(".pnp.cjs").writeText("// Plug'n'Play runtime\n")
        val location = dir.resolve("cache/dequal/")
        pkg(location, "export declare function dequal(): boolean;\n")
        val answer = """{"dequal": "$location/"}"""

        val text = adapter { ProcessResult(0, "$answer\n") }.ground(repo.toRealPath(), change(), listOf(error("src/same.ts")))

        assertEquals("export declare function dequal(): boolean;", text.lines().last())
        assertEquals(listOf("yarn", "node", "-e"), commands.single().take(3))
        assertEquals(listOf("${repo.toRealPath().resolve("src")}/", "dequal", "@types/dequal"), commands.single().drop(4))
    }

    @Test
    fun `a Plug'n'Play lookup that fails finds nothing`() {
        fixture("yarn-berry-pnp")
        repo.resolve(".pnp.cjs").writeText("// Plug'n'Play runtime\n")

        assertEquals(
            "",
            adapter { ProcessResult(1, "Error: Cannot find module 'pnpapi'") }.ground(repo, change(), listOf(error("src/same.ts"))),
        )
        assertEquals("", adapter { ProcessResult(0, "{not json") }.ground(repo, change(), listOf(error("src/same.ts"))))
        assertEquals("", adapter { ProcessResult(0, "{\"dequal\": 1}") }.ground(repo, change(), listOf(error("src/same.ts"))))
        assertEquals(
            "",
            adapter { throw ProcessTimedOut("yarn did not finish within 2m") }.ground(repo, change(), listOf(error("src/same.ts"))),
        )
    }

    @Test
    fun `processes start the program the child's PATH names, on the selected Node`() {
        fixture("yarn-classic")
        val bin = dir.resolve("bin").createDirectories()
        script(bin.resolve("yarn"), "#!/bin/sh\nexit 0\n")
        val node = node("24")
        script(node.resolve("bin/yarn").createParentDirectories(), "#!/bin/sh\necho \"\$@\" >> ${dir.resolve("node-yarn.log")}\nexit 0\n")

        assertEquals(emptyList(), TypeScriptAdapter(environment = mapOf("PATH" to bin.toString())).verify(repo, 1.minutes))
        assertEquals(
            emptyList(),
            TypeScriptAdapter(nodes = mapOf(24 to node), environment = mapOf("PATH" to bin.toString())).verify(repo, 1.minutes),
        )
        assertTrue(
            dir
                .resolve("node-yarn.log")
                .toFile()
                .readText()
                .startsWith("install --frozen-lockfile"),
        )
    }

    @Test
    fun `a program is found in the PATH's directories, or left to the system`() {
        val bin = dir.resolve("bin").createDirectories()
        script(bin.resolve("pnpm"), "#!/bin/sh\n")
        bin.resolve("npm").writeText("not executable")

        assertEquals(bin.resolve("pnpm").toString(), executable("pnpm", "::/nowhere:$bin"))
        assertEquals("npm", executable("npm", bin.toString()))
        assertEquals("/usr/bin/env", executable("/usr/bin/env", bin.toString()))
        assertEquals("pnpm", executable("pnpm", null))
    }

    private fun adapter(
        nodes: Map<Int, Path> = emptyMap(),
        answer: (List<String>) -> ProcessResult = { ProcessResult(0, "") },
    ) = TypeScriptAdapter(nodes = nodes) { command, _, _, _ ->
        commands += command
        answer(command)
    }

    private fun ground(file: String) = adapter().ground(repo.toRealPath(), change(), listOf(error(file)))

    private fun change() = DependencyChange("npm", "dequal", "2.0.2", "2.0.3", UpdateType.PATCH)

    private fun error(file: String) = Diagnostic(file, 1, 1, "TS2554", "Expected 2 arguments.", Severity.ERROR, "tsc")

    private fun pkg(
        dir: Path,
        declarations: String,
    ) {
        dir.createDirectories().resolve("package.json").writeText("""{"types": "index.d.ts"}""")
        dir.resolve("index.d.ts").writeText(declarations)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun fixture(name: String) {
        requireNotNull(
            javaClass.getResource("/fixtures/$name"),
        ).toURI().toPath().copyToRecursively(repo, followLinks = false, overwrite = false)
    }

    private fun script(
        file: Path,
        text: String,
    ) {
        file.writeText(text)
        file.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    private fun node(major: String): Path {
        val home = dir.resolve("node-$major")
        home.resolve("include/node").createDirectories().resolve("node_version.h").writeText(
            "#define NODE_MAJOR_VERSION $major\n#define NODE_MINOR_VERSION 0\n#define NODE_PATCH_VERSION 0\n",
        )
        return home
    }
}
