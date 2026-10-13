package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.UpdateType
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the types of a package are (AC26), re-exports (AC27), and which declarations the model gets. */
class DeclarationGroundingTest {
    @TempDir
    lateinit var dir: Path

    private val repo by lazy { dir.resolve("repo").createDirectories().toRealPath() }
    private val change = DependencyChange("npm", "deq", "1.0.0", "2.0.0", UpdateType.MAJOR)
    private val located = mutableMapOf<String, PackageFiles>()
    private val grounding = DeclarationGrounding { _, _, names -> names.mapNotNull { name -> located[name]?.let { name to it } }.toMap() }

    @Test
    fun `the types condition of the imported subpath in exports comes first`() {
        source("import { dequal } from \"deq\";\nimport { helper } from \"deq/sub\";\n")
        pkg(
            "deq",
            "package.json" to
                """{"types": "./wrong.d.ts", "exports": {".": {"import": """ +
                """{"types": "./dist/index.d.mts", "default": "./dist/index.mjs"}}, """ +
                """"./sub": {"types": {"import": "./dist/sub.d.mts"}}, "./package.json": "./package.json"}}""",
            "wrong.d.ts" to "export declare function dequal(): never;\n",
            "dist/index.d.mts" to "export declare function dequal(a: unknown, b: unknown): boolean;\n",
            "dist/sub.d.mts" to "export declare function helper(): void;\n",
        )

        assertEquals(
            "Type declarations of deq 2.0.0:\n\n" +
                "// deq/dist/index.d.mts\nexport declare function dequal(a: unknown, b: unknown): boolean;\n\n" +
                "// deq/dist/sub.d.mts\nexport declare function helper(): void;",
            ground(),
        )
    }

    @Test
    fun `exports without a subpath map are the root's conditions, also as a list, and a string has no types`() {
        source("import { dequal } from \"deq\";\n")
        pkg(
            "deq",
            "package.json" to """{"exports": {"import": "./index.mjs", "types": "./types/index.d.ts"}}""",
            "types/index.d.ts" to "export declare function dequal(): boolean;\n",
        )
        assertEquals("// deq/types/index.d.ts", ground().lines()[2])

        pkg("deq", "package.json" to """{"exports": [{"types": "./a.d.ts"}]}""", "a.d.ts" to "export declare function dequal(): 1;\n")
        assertEquals("// deq/a.d.ts", ground().lines()[2])

        pkg("deq", "package.json" to """{"exports": "./index.js"}""", "index.d.ts" to "export declare function dequal(): 2;\n")
        assertEquals("// deq/index.d.ts", ground().lines()[2])

        pkg("deq", "package.json" to """{"exports": ["./index.js"]}""", "index.d.ts" to "export declare function dequal(): 3;\n")
        assertEquals("// deq/index.d.ts", ground().lines()[2])
    }

    @Test
    fun `then types or typings`() {
        source("import { dequal } from \"deq\";\n")
        pkg("deq", "package.json" to """{"types": "./lib/main.d.ts"}""", "lib/main.d.ts" to "export declare function dequal(): boolean;\n")
        assertEquals("// deq/lib/main.d.ts", ground().lines()[2])

        pkg(
            "deq",
            "package.json" to """{"typings": "lib/other.d.ts"}""",
            "lib/other.d.ts" to "export declare function dequal(): boolean;\n",
        )
        assertEquals("// deq/lib/other.d.ts", ground().lines()[2])
    }

    @Test
    fun `then index_d_ts, also for a package_json that isn't JSON`() {
        source("import { dequal } from \"deq\";\nimport { helper } from \"deq/sub\";\nimport { more } from \"deq/more\";\n")
        pkg(
            "deq",
            "package.json" to "{not json",
            "index.d.ts" to "export declare function dequal(): boolean;\n",
            "sub.d.ts" to "export declare function helper(): void;\n",
            "more/index.d.ts" to "export declare function more(): void;\n",
        )

        assertEquals(
            listOf("// deq/index.d.ts", "// deq/sub.d.ts", "// deq/more/index.d.ts"),
            ground().lines().filter { it.startsWith("//") },
        )
    }

    @Test
    fun `then the types package`() {
        source("import { dequal } from \"deq\";\n")
        pkg("deq", "package.json" to """{"main": "index.js"}""", "index.js" to "module.exports = {};\n")
        pkg("@types/deq", "package.json" to """{"types": "index.d.ts"}""", "index.d.ts" to "export declare function dequal(): boolean;\n")

        assertEquals("// @types/deq/index.d.ts", ground().lines()[2])
        assertEquals("@types/scope__pkg", typesPackage("@scope/pkg"))
    }

    @Test
    fun `relative re-exports lead to the declaring file, the symbols of the errors and the imports are chosen`() {
        source("import { dequal } from \"deq\";\nconst x: Options = {};\n")
        pkg(
            "deq",
            "package.json" to """{"types": "./dist/index.d.ts"}""",
            "dist/index.d.ts" to
                "export * from './impl';\nexport { Options, type Mode } from \"./options.js\";\nexport * from 'other-package';\n" +
                "export * as utils from './missing';\nexport default dequal;\n",
            "dist/impl.d.ts" to
                "export declare function dequal(a: unknown): boolean;\nexport declare function dequal(a: unknown, b: unknown): boolean;\n" +
                "export declare function unrelated(): void;\nexport declare const VERSION: string;\n",
            "dist/options/index.d.ts" to
                "export interface Options {\n  deep?: boolean;\n}\nexport type Mode = \"a\" | \"b\";\ndeclare namespace inner {}\nexport {};\n",
        )

        val text = ground("Cannot find name 'Options'.")

        assertEquals(
            "Type declarations of deq 2.0.0:\n\n" +
                "// deq/dist/impl.d.ts\nexport declare function dequal(a: unknown): boolean;\n\n" +
                "// deq/dist/impl.d.ts\nexport declare function dequal(a: unknown, b: unknown): boolean;\n\n" +
                "// deq/dist/options/index.d.ts\nexport interface Options {\n  deep?: boolean;\n}",
            text,
        )
    }

    @Test
    fun `declarations are read from a zip archive, as Plug'n'Play keeps them, also through a virtual path`() {
        source("import { dequal } from \"deq\";\n")
        val zip = dir.resolve("cache/deq-npm-2.0.0.zip").createParentDirectories()
        ZipOutputStream(zip.outputStream()).use { out ->
            mapOf(
                "node_modules/deq/package.json" to """{"types": "index.d.ts"}""",
                "node_modules/deq/index.d.ts" to "export declare function dequal(): boolean;\n",
            ).forEach { (name, text) ->
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray())
                out.closeEntry()
            }
        }
        located["deq"] = PackageFiles.at("$zip/node_modules/deq/")
        assertEquals("// deq/index.d.ts", ground().lines()[2])

        val virtual = dir.resolve("project/.yarn/__virtual__/deq-virtual-1234/2/cache/deq-npm-2.0.0.zip/node_modules/deq/")
        located["deq"] = PackageFiles.at(virtual.toString() + "/")
        assertEquals("// deq/index.d.ts", ground().lines()[2])

        assertEquals(null, PackageFiles.at("$zip/node_modules/deq/").read("missing.d.ts"))
        located["deq"] = PackageFiles.at(dir.resolve("cache/missing.zip/node_modules/deq/").toString())
        assertEquals("", ground())
    }

    @Test
    fun `without a symbol, a package, or a declaration it names, there is no grounding`() {
        source("import * as deq from \"deq\";\nimport { other } from \"other\";\n")
        assertEquals("", grounding.ground(repo, change, listOf(Diagnostic(null, null, null, null, "no names", Severity.ERROR, "tsc"))))
        assertEquals("", ground("Cannot find name 'dequal'."), "no package")

        pkg("deq", "package.json" to "{}", "index.d.ts" to "export declare function dequal(): boolean;\n")
        assertEquals("", ground("Cannot find name 'missing'."), "nothing named")

        pkg("deq", "package.json" to "{}")
        assertEquals("", ground("Cannot find name 'dequal'."), "no declaration file")
    }

    @Test
    fun `the declarations stop before they outgrow the budget`() {
        source("import { a, b } from \"deq\";\n")
        val big = "export declare function a(): \"${"x".repeat(15_000)}\";\nexport declare function b(): \"${"y".repeat(15_000)}\";\n"
        pkg("deq", "package.json" to "{}", "index.d.ts" to big)

        assertEquals(1, ground().lines().count { it.startsWith("//") })
    }

    @Test
    fun `tsx sources are read with their grammar`() {
        repo.resolve("src/App.tsx").createParentDirectories().writeText("import { dequal } from \"deq\";\nexport const A = () => <b/>;\n")
        pkg("deq", "package.json" to "{}", "index.d.ts" to "export declare function dequal(): boolean;\n")

        assertEquals("// deq/index.d.ts", grounding.ground(repo, change, listOf(diagnostic("src/App.tsx", "x"))).lines()[2])
    }

    private fun ground(message: String = "Expected 2 arguments.") =
        grounding.ground(repo, change, listOf(diagnostic("src/app.ts", message)))

    private fun diagnostic(
        file: String,
        message: String,
    ) = Diagnostic(file, 1, 1, "TS2554", message, Severity.ERROR, "tsc")

    private fun source(text: String) = repo.resolve("src/app.ts").createParentDirectories().writeText(text)

    private fun pkg(
        name: String,
        vararg files: Pair<String, String>,
    ) {
        val root = dir.resolve("packages-${located.size}-$name").createDirectories()
        files.forEach { (path, text) -> root.resolve(path).createParentDirectories().writeText(text) }
        located[name] = PackageFiles.at(root.toString())
    }

    @Test
    fun `conditions are searched in order, lists included, and a subpath exports doesn't name has no types there`() {
        source(
            "import { dequal } from \"deq\";\nimport { helper } from \"deq/sub\";\nimport { other } from \"deq/other\";\n" +
                "import { x } from \"deq/x\";\nimport { y } from \"deq/y\";\n",
        )
        pkg(
            "deq",
            "package.json" to
                """{"exports": {".": {"node": "./n.js", "import": """ +
                """{"default": "./i.mjs", "types": {"other": [], "import": "./dist/index.d.mts"}}}, """ +
                """"./sub": {"types": ["./dist/sub.d.mts"]}, "./x": {"import": "./x.mjs"}, "./y": {"types": {"import": []}}}}""",
            "dist/index.d.mts" to "export declare function dequal(): boolean;\n",
            "dist/sub.d.mts" to "export declare function helper(): void;\n",
            "other.d.ts" to "export declare function other(): void;\n",
        )

        assertEquals(
            listOf("// deq/dist/index.d.mts", "// deq/dist/sub.d.mts", "// deq/other.d.ts"),
            ground().lines().filter {
                it.startsWith("//")
            },
        )

        pkg(
            "deq",
            "package.json" to """{"exports": ["./x.js", {"types": "./a.d.ts"}]}""",
            "a.d.ts" to "export declare function dequal(): 1;\n",
        )
        assertEquals("// deq/a.d.ts", ground().lines()[2])
    }

    @Test
    fun `an error without a file is grounded from the package root, and a package_json that isn't an object is no manifest`() {
        pkg("deq", "package.json" to "[]", "index.d.ts" to "import type { X } from \"x\";\nexport declare function dequal(): boolean;\n")

        val text =
            grounding.ground(
                repo,
                change,
                listOf(Diagnostic(null, null, null, "TS2305", "Module '\"deq\"' has no exported member 'dequal'.", Severity.ERROR, "tsc")),
            )

        assertEquals("// deq/index.d.ts", text.lines()[2])
    }

    @Test
    fun `re-exports that lead in a circle are read once, and at most 200 files are read`() {
        source("import { dequal, last } from \"deq\";\n")
        pkg(
            "deq",
            "package.json" to "{}",
            "index.d.ts" to "export * from './a';\nexport * from './chain0';\n",
            "a.d.ts" to "export * from './index';\nexport declare function dequal(): boolean;\n",
            *(0..200).map { "chain$it.d.ts" to "export * from './chain${it + 1}';\n" }.toTypedArray(),
            "chain201.d.ts" to "export declare function last(): void;\n",
        )

        assertEquals(listOf("// deq/a.d.ts"), ground().lines().filter { it.startsWith("//") })
    }

    @Test
    fun `package files - a missing file, a virtual path without a trailing slash, and paths that leave the package`() {
        val root = dir.resolve("p").createDirectories()
        root.resolve("a.d.ts").writeText("x")

        assertEquals(null, PackageFiles.at(root.toString()).read("b.d.ts"))
        assertEquals("x", PackageFiles.at(dir.resolve("project/.yarn/__virtual__/p-virtual-1/2/p").toString()).read("a.d.ts"))
        assertEquals("b/c", normalizePackagePath("./a/../b//c"))
        assertEquals(null, normalizePackagePath("../outside"))
    }
}
