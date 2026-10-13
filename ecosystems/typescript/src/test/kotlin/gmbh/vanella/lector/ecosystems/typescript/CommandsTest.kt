package gmbh.vanella.lector.ecosystems.typescript

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/** The command lines per package manager (AC13, AC24), and what the adapter reads from tsconfig.json. */
class CommandsTest {
    @TempDir
    lateinit var dir: Path

    private val app =
        NodePackage("packages/app", PackageJson("app", mapOf("typescript" to "5.9.3"), emptyMap(), emptyList(), null, null), true)
    private val lib = app.copy(dir = "packages/lib", manifest = app.manifest.copy(dependencies = emptyMap()))

    @Test
    fun `the first install is frozen, never without scripts, and the one after a build-file edit isn't`() {
        val frozen = PackageManager.entries.map { Commands(it).install(frozen = true) }
        val unfrozen = PackageManager.entries.map { Commands(it).install(frozen = false) }

        assertEquals(
            listOf(
                listOf("npm", "ci"),
                listOf("pnpm", "install", "--frozen-lockfile"),
                listOf("yarn", "install", "--frozen-lockfile"),
                listOf("yarn", "install", "--immutable"),
            ),
            frozen,
        )
        assertEquals(
            listOf(
                listOf("npm", "install"),
                listOf("pnpm", "install", "--no-frozen-lockfile"),
                listOf("yarn", "install"),
                listOf("yarn", "install", "--no-immutable"),
            ),
            unfrozen,
        )
        assertEquals(emptyList(), (frozen + unfrozen).flatten().filter { it == "--ignore-scripts" })
    }

    @Test
    fun `tools run through the package manager, under Yarn Berry from the root if only the root has them`() {
        val tsc = { manager: PackageManager, pkg: NodePackage -> Commands(manager).exec(pkg, "typescript", "tsc", listOf("--noEmit")) }

        assertEquals(listOf("npm", "exec", "--no", "--", "tsc", "--noEmit"), tsc(PackageManager.NPM, app))
        assertEquals(listOf("pnpm", "exec", "tsc", "--noEmit"), tsc(PackageManager.PNPM, app))
        assertEquals(listOf("yarn", "run", "tsc", "--noEmit"), tsc(PackageManager.YARN_CLASSIC, app))
        assertEquals(listOf("yarn", "run", "tsc", "--noEmit"), tsc(PackageManager.YARN_BERRY, app))
        assertEquals(listOf("yarn", "run", "-T", "tsc", "--noEmit"), tsc(PackageManager.YARN_BERRY, lib))
        assertEquals(listOf("pnpm", "run", "build"), Commands(PackageManager.PNPM).script("build"))
        assertEquals(listOf("yarn", "test"), Commands(PackageManager.YARN_BERRY).test())
    }

    @Test
    fun `tsconfig_json with comments and trailing commas, its references, and whether it compiles anything`() {
        val tsconfig = dir.resolve("tsconfig.json")
        val read = { text: String -> Tsconfig.read(tsconfig.also { it.writeText(text) }) }

        assertEquals(
            Tsconfig(references = true, compilesNothing = false),
            read("""{ /* solution */ "files": [], "references": [{"path": "a"},], }"""),
        )
        assertEquals(Tsconfig(references = false, compilesNothing = true), read("""{"files": [] // base config only""" + "\n}"))
        assertEquals(Tsconfig(references = false, compilesNothing = false), read("""{"files": [], "include": ["src"]}"""))
        assertEquals(Tsconfig(references = false, compilesNothing = false), read("""{"files": ["a.ts"], "references": []}"""))
        assertEquals(Tsconfig(references = false, compilesNothing = false), read("""{"compilerOptions": {}}"""))
        assertEquals(Tsconfig(references = false, compilesNothing = false), read("""[]"""))
        assertEquals(Tsconfig(references = false, compilesNothing = false), read("""{"""), "tsc reports what is wrong with it")
    }

    @Test
    fun `a JavaScript regex that matches text literally`() {
        assertEquals("""a\.b\*\(c\)\/d \[e\] \${'$'}f\^ \{g\} h\? i\+ j\| k\\""", jsRegex("""a.b*(c)/d [e] ${'$'}f^ {g} h? i+ j| k\"""))
    }
}
