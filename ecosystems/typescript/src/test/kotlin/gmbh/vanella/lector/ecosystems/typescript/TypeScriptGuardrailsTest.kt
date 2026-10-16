package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.DEFAULT_MAX_CHANGED_LINES
import gmbh.vanella.lector.core.FileChange
import gmbh.vanella.lector.core.Guardrails
import gmbh.vanella.lector.core.Violation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The TypeScript adapter's part of the scope and build-file guardrails (spec section 8). */
class TypeScriptGuardrailsTest {
    private val adapter = TypeScriptAdapter { _, _, _, _ -> error("no process here") }
    private val guardrails = Guardrails(adapter, DEFAULT_MAX_CHANGED_LINES)

    private val tsconfig =
        """
        {
          "compilerOptions": {
            "target": "es2022",
            "module": "nodenext"
          },
          "include": ["src"],
          "exclude": ["node_modules"]
        }
        """.trimIndent()

    @Test
    fun `package_json, tsconfig files, the workspace file, and the test runners' configuration are build files`() {
        for (file in listOf(
            "package.json",
            "packages/app/package.json",
            "tsconfig.json",
            "tsconfig.build.json",
            "packages/app/tsconfig.app.json",
            "pnpm-workspace.yaml",
            "jest.config.js",
            "jest.config.ts",
            "jest.config.mjs",
            "vitest.config.ts",
            "packages/app/vitest.config.mts",
            "vite.config.ts",
            "vitest.workspace.ts",
            "jest.config.cjs",
            "jest.config.json",
            "vitest.workspace.json",
            "jest.unit.config.js",
            "packages/app/vitest.e2e.config.ts",
            "jest.preset.js",
            "test/jest-e2e.json",
            ".pnpmfile.cjs",
        )) {
            assertTrue(adapter.isBuildFile(file), file)
        }
        for (file in listOf(
            "src/index.ts",
            "src/tsconfig.ts",
            "README.md",
            "package-lock.json",
            "jest.setup.ts",
            "babel.config.js",
            "vitest.setup.ts",
        )) {
            assertFalse(adapter.isBuildFile(file), file)
        }
    }

    @Test
    fun `sources in every language, tests, and build files are editable`() {
        for (file in listOf(
            "src/index.ts",
            "src/App.tsx",
            "lib/util.js",
            "scripts/setup.mjs",
            "config/legacy.cjs",
            "src/a.jsx",
            "packages/app/src/welcome.test.ts",
            "package.json",
            "tsconfig.json",
            "jest.config.ts",
        )) {
            assertTrue(adapter.isEditable(file), file)
        }
    }

    @Test
    fun `lockfiles, registry configuration, installed packages, build output, and generated code are not (AC39)`() {
        for (file in listOf(
            "package-lock.json",
            "packages/app/package-lock.json",
            "npm-shrinkwrap.json",
            "pnpm-lock.yaml",
            "yarn.lock",
            ".npmrc",
            ".yarnrc",
            ".yarnrc.yml",
            ".pnp.cjs",
            ".pnp.loader.mjs",
            "node_modules/dequal/index.d.ts",
            "packages/app/node_modules/x/index.js",
            ".yarn/releases/yarn-4.18.1.cjs",
            "dist/index.js",
            "packages/lib/dist/index.d.ts",
            "build/main.js",
            "out/app.js",
            "coverage/lcov-report/index.html",
            ".next/server/page.js",
            "src/generated/client.ts",
            "src/api/__generated__/types.ts",
            "src/schema.generated.ts",
            "src/routes.gen.ts",
        )) {
            assertFalse(adapter.isEditable(file), file)
        }
    }

    @Test
    fun `each tsconfig option that loosens the type check is rejected (AC36)`() {
        val loosening =
            listOf(
                "\"strict\": false," to "`strict: false` loosens the type check",
                "\"strictNullChecks\": false," to "`strictNullChecks: false` loosens the type check",
                "\"noImplicitAny\": false," to "`noImplicitAny: false` loosens the type check",
                "\"noImplicitReturns\": false," to "`noImplicitReturns: false` loosens the type check",
                "\"skipLibCheck\": true," to "`skipLibCheck: true` skips the type check of declaration files",
                "\"noCheck\": true," to "`noCheck` turns the type check off",
                "\"checkJs\": false," to "`checkJs: false` turns the type check of JavaScript off",
            )
        for ((option, reason) in loosening) {
            val after = tsconfig.replace("\"target\": \"es2022\",", "\"target\": \"es2022\",\n    $option")

            assertEquals(listOf(Violation("build-file", "tsconfig.json", 4, reason)), check("tsconfig.json", tsconfig, after), option)
        }
    }

    @Test
    fun `a new exclude entry in a tsconfig is rejected, on its key's line or inside the list (AC36)`() {
        val inList = tsconfig.replace("\"exclude\": [\"node_modules\"]", "\"exclude\": [\n    \"node_modules\",\n    \"src/legacy\"\n  ]")
        val newKey = tsconfig.replace("\"include\": [\"src\"],", "\"include\": [\"src\"],\n  \"exclude\": [\"src/legacy\"],")

        assertEquals(
            listOf(Violation("build-file", "tsconfig.json", 9, "a new `exclude` entry leaves files out of the type check")),
            check("tsconfig.json", tsconfig, inList).filter { it.line == 9 },
        )
        assertEquals(
            listOf(Violation("build-file", "tsconfig.base.json", 7, "a new `exclude` entry leaves files out of the type check")),
            check("tsconfig.base.json", tsconfig, newKey),
        )
    }

    @Test
    fun `fixes that tighten or only change the compiler are allowed, like moduleResolution bundler (AC38)`() {
        val allowed =
            listOf(
                "\"moduleResolution\": \"bundler\",",
                "\"strict\": true,",
                "\"skipLibCheck\": false,",
                "\"types\": [\"node\", \"jest\"],",
            )
        for (option in allowed) {
            val after = tsconfig.replace("\"target\": \"es2022\",", "\"target\": \"es2022\",\n    $option")

            assertEquals(emptyList(), check("tsconfig.json", tsconfig, after), option)
        }
        val excludeFirst = "{\n  \"exclude\": [\"node_modules\"],\n  \"include\": [\"src\"]\n}"
        val afterExclude = excludeFirst.replace("\"include\": [\"src\"]", "\"include\": [\"src\"],\n  \"files\": []")
        assertEquals(emptyList(), check("tsconfig.json", excludeFirst, afterExclude), "lines after a closed exclude list")
    }

    @Test
    fun `a change to the test script, and options that leave tests out, are rejected in package_json (AC37)`() {
        val before =
            "{\n  \"scripts\": {\n    \"build\": \"tsc\",\n    \"test\": \"jest\"\n  },\n" +
                "  \"jest\": {\n    \"preset\": \"ts-jest\"\n  }\n}"
        val cases =
            listOf(
                before.replace(
                    "\"test\": \"jest\"",
                    "\"test\": \"jest src/ok\"",
                ) to "a change to a test script changes which tests run",
                before.replace("\"preset\": \"ts-jest\"", "\"preset\": \"ts-jest\",\n    \"testPathIgnorePatterns\": [\"slow\"]") to
                    "a new `testPathIgnorePatterns` entry leaves tests out",
                before.replace("\"preset\": \"ts-jest\"", "\"preset\": \"ts-jest\",\n    \"modulePathIgnorePatterns\": [\"x\"]") to
                    "a new `modulePathIgnorePatterns` entry leaves tests out",
                before.replace("\"preset\": \"ts-jest\"", "\"preset\": \"ts-jest\",\n    \"passWithNoTests\": true") to
                    "`passWithNoTests` leaves tests out",
                before.replace("\"preset\": \"ts-jest\"", "\"preset\": \"ts-jest\",\n    \"exclude\": [\"x\"]") to
                    "a new `exclude` entry leaves tests out",
            )
        for ((after, reason) in cases) {
            assertEquals(listOf(reason), check("package.json", before, after).map { it.reason }, reason)
        }
        assertEquals(emptyList(), check("package.json", before, before.replace("\"build\": \"tsc\"", "\"build\": \"tsc -b\"")))
    }

    @Test
    fun `options that leave tests out are rejected in the Jest and Vitest configuration (AC37)`() {
        val jest = "module.exports = {\n  preset: \"ts-jest\",\n};\n"
        val vitest = "export default defineConfig({\n  test: {\n    environment: \"node\",\n  },\n});\n"

        assertEquals(
            listOf("a new `testPathIgnorePatterns` entry leaves tests out"),
            check(
                "jest.config.js",
                jest,
                jest.replace("preset: \"ts-jest\",", "preset: \"ts-jest\",\n  testPathIgnorePatterns: [\"/slow/\"],"),
            ).map { it.reason },
        )
        assertEquals(
            listOf(4, 5, 6),
            check(
                "vitest.config.ts",
                vitest,
                vitest.replace("environment: \"node\",", "environment: \"node\",\n    exclude: [\n      \"slow/**\",\n    ],"),
            ).filter { it.reason == "a new `exclude` entry leaves tests out" }
                .map { it.line },
        )
        assertEquals(
            listOf("`passWithNoTests` leaves tests out"),
            check(
                "packages/app/vite.config.mts",
                vitest,
                vitest.replace("environment: \"node\",", "passWithNoTests: true,"),
            ).map { it.reason },
        )
        assertEquals(
            emptyList(),
            check("jest.config.js", jest, jest.replace("preset: \"ts-jest\",", "preset: \"ts-jest\",\n  testTimeout: 10000,")),
        )
    }

    @Test
    fun `a new entry in a list that leaves tests out is rejected inside the list too, in each place Jest reads it`() {
        val jest = "module.exports = {\n  testPathIgnorePatterns: [\n    \"/node_modules/\",\n  ],\n};\n"
        val added = jest.replace("\"/node_modules/\",", "\"/node_modules/\",\n    \"/src/broken.test.ts\",")
        val manifest = "{\n  \"jest\": {\n    \"modulePathIgnorePatterns\": [\n      \"dist\"\n    ]\n  }\n}\n"

        assertEquals(
            listOf(Violation("build-file", "jest.config.js", 4, "a new `testPathIgnorePatterns` entry leaves tests out")),
            check("jest.config.js", jest, added),
        )
        assertEquals(
            listOf("`passWithNoTests` leaves tests out"),
            check("jest.config.json", "{\n}\n", "{\n  \"passWithNoTests\": true\n}\n").map { it.reason },
        )
        assertEquals(
            listOf(4, 5),
            check("package.json", manifest, manifest.replace("\"dist\"", "\"dist\",\n      \"src\"")).map { it.line },
            "the comma changes the line before, inside the list too",
        )
    }

    @Test
    fun `options that narrow which tests run are rejected, and Vitest's include only in its test options`() {
        val jest = "module.exports = {\n  preset: \"ts-jest\",\n};\n"
        for (option in listOf(
            "testMatch: [\"**/ok.test.ts\"],",
            "testRegex: \"ok\\\\.test\\\\.ts$\",",
            "roots: [\"<rootDir>/src/ok\"],",
            "testNamePattern: \"adds\",",
        )) {
            val after = jest.replace("preset: \"ts-jest\",", "preset: \"ts-jest\",\n  $option")
            assertEquals(listOf(3), check("jest.config.ts", jest, after).map { it.line }, option)
        }
        val vitest =
            "export default defineConfig({\n  optimizeDeps: {\n    include: [],\n  },\n  test: {\n    environment: \"node\",\n  },\n});\n"
        assertEquals(
            listOf("`include` changes which tests run"),
            check(
                "vitest.config.ts",
                vitest,
                vitest.replace("environment: \"node\",", "environment: \"node\",\n    include: [\"src/ok/**\"],"),
            ).map { it.reason },
        )
        assertEquals(
            emptyList(),
            check("vite.config.ts", vitest, vitest.replace("include: [],", "include: [\"dequal\"],")),
            "Vite's own include is no test option",
        )
        assertEquals(emptyList(), check("vitest.config.ts", "", "include: [\"src\"]\n"), "nor one outside any object")
    }

    @Test
    fun `every script the test script runs through, and pre and post scripts, are guarded, also on one line`() {
        val before = "{\n  \"scripts\": {\n    \"test\": \"npm run test:unit\",\n    \"test:unit\": \"jest\"\n  }\n}\n"
        val cases =
            listOf(
                before.replace("\"test:unit\": \"jest\"", "\"test:unit\": \"jest src/ok\""),
                before.replace("\"test:unit\": \"jest\"", "\"test:unit\": \"jest\",\n    \"pretest\": \"rm src/broken.test.ts\""),
                before.replace("\"test:unit\": \"jest\"", "\"test:unit\": \"jest\",\n    \"posttest\": \"exit 0\""),
            )
        for (after in cases) {
            assertEquals(
                listOf("a change to a test script changes which tests run"),
                check("package.json", before, after).map { it.reason }.distinct(),
            )
        }
        val oneLine = "{\"scripts\": {\"build\": \"tsc\", \"test\": \"jest\"}}\n"
        assertEquals(1, check("package.json", oneLine, oneLine.replace("\"jest\"", "\"jest src/ok\"")).size)
        assertEquals(
            emptyList(),
            check(
                "package.json",
                before,
                before.replace("\"scripts\": {", "\"scripts\": {\n    \"testing-library\": \"x\","),
            ),
            "a script whose name only starts like one",
        )
    }

    @Test
    fun `a line after an exclude list that closed is no exclude entry`() {
        val before = "{\n  \"exclude\": [\"dist\"],\n  \"compilerOptions\": {\n    \"types\": [\n      \"node\"\n    ]\n  }\n}\n"
        val after = before.replace("\"node\"", "\"node\",\n      \"vitest/globals\"")

        assertEquals(emptyList(), check("tsconfig.json", before, after))
    }

    @Test
    fun `a new module declaration without a body is rejected as a suppression, one with types isn't (D3)`() {
        val before = "export {};\n"

        assertEquals(
            listOf(Violation("suppression", "src/global.d.ts", 2, "the edits add the suppression `declare module \"dep\"`")),
            check("src/global.d.ts", before, "$before" + "declare module \"dep\";\n"),
        )
        assertEquals(
            emptyList(),
            check("src/global.d.ts", before, "$before" + "declare module \"dep\" {\n  export function f(): void;\n}\n"),
        )
    }

    @Test
    fun `removing a test script, or renaming it, runs no tests and is rejected (D1)`() {
        val before = "{\n  \"scripts\": {\n    \"build\": \"tsc\",\n    \"test\": \"jest\"\n  }\n}\n"
        val removed = before.replace(",\n    \"test\": \"jest\"", "")
        val renamed = before.replace("\"test\": \"jest\"", "\"check\": \"jest\"")

        assertEquals(
            listOf(Violation("build-file", "packages/app/package.json", null, "removing the `test` script runs no tests")),
            check("packages/app/package.json", before, removed),
        )
        assertEquals(listOf("removing the `test` script runs no tests"), check("package.json", before, renamed).map { it.reason })
        assertEquals(emptyList(), check("package.json", before, before.replace("    \"build\": \"tsc\",\n", "")), "another script")
    }

    @Test
    fun `removing a strict option, or the base that sets it, loosens the type check and is rejected (D1)`() {
        val before =
            "{\n  \"extends\": \"./tsconfig.base.json\",\n  \"compilerOptions\": {\n    \"strict\": true,\n" +
                "    \"noImplicitOverride\": true,\n    \"checkJs\": true,\n    \"skipLibCheck\": false\n  }\n}\n"
        val cases =
            listOf(
                before.replace("    \"strict\": true,\n", "") to "removing `strict: true` loosens the type check",
                before.replace("    \"strict\": true,", "    // \"strict\": true,") to "removing `strict: true` loosens the type check",
                before.replace("    \"noImplicitOverride\": true,\n", "") to "removing `noImplicitOverride: true` loosens the type check",
                before.replace("    \"checkJs\": true,\n", "") to "removing `checkJs: true` loosens the type check",
                before.replace("  \"extends\": \"./tsconfig.base.json\",\n", "") to
                    "removing `extends` drops the options of `./tsconfig.base.json`",
            )
        for ((after, reason) in cases) {
            assertEquals(listOf(reason), check("tsconfig.json", before, after).map { it.reason }, reason)
        }
        assertEquals(emptyList(), check("tsconfig.json", before, before.replace(",\n    \"skipLibCheck\": false", "")))
    }

    @Test
    fun `narrowing what the type check covers is rejected, widening it isn't (D1)`() {
        val before = "{\n  \"include\": [\"src\", \"test\"]\n}\n"
        assertEquals(
            listOf("the type check no longer covers `test`"),
            check("tsconfig.json", before, "{\n  \"include\": [\"src\"]\n}\n").map { it.reason },
        )
        assertEquals(
            listOf("the type check no longer covers `src`, `test`"),
            check("tsconfig.json", before, "{\n  \"files\": []\n}\n").map { it.reason },
        )
        assertEquals(
            listOf("a new `include` or `files` list narrows the type check, which covered every file"),
            check("tsconfig.json", "{\n}\n", "{\n  \"include\": [\"src\"]\n}\n").map { it.reason },
        )
        assertEquals(emptyList(), check("tsconfig.json", before, "{\n  \"include\": [\"src\", \"test\", \"types\"]\n}\n"))
        assertEquals(emptyList(), check("tsconfig.json", before, "{\n}\n"), "no list covers every file")

        val solution =
            "{\n  \"files\": [],\n  \"references\": [\n    { \"path\": \"./packages/lib\" },\n    { \"path\": \"./packages/app\" }\n  ]\n}\n"
        assertEquals(
            listOf("the type check no longer covers the project `./packages/app`"),
            check("tsconfig.json", solution, solution.replace(",\n    { \"path\": \"./packages/app\" }", "")).map { it.reason },
        )
    }

    @Test
    fun `narrowing the workspaces leaves packages out and is rejected, in package_json and pnpm-workspace_yaml (D1)`() {
        val manifest = "{\n  \"workspaces\": [\n    \"packages/*\"\n  ]\n}\n"
        assertEquals(
            listOf("the workspaces no longer include `packages/*`"),
            check("package.json", manifest, manifest.replace("packages/*", "packages/lib")).map { it.reason },
        )
        assertEquals(
            listOf("the workspaces newly exclude `!packages/app`"),
            check("package.json", manifest, manifest.replace("\"packages/*\"", "\"packages/*\",\n    \"!packages/app\"")).map {
                it.reason
            },
        )
        val yaml = "packages:\n  - \"packages/*\"\n"
        assertEquals(
            listOf("the workspaces newly exclude `!packages/app`"),
            check("pnpm-workspace.yaml", yaml, "$yaml  - \"!packages/app\"\n").map { it.reason },
        )
        assertEquals(emptyList(), check("pnpm-workspace.yaml", yaml, "$yaml  - \"tools/*\"\n"), "a new package")
        assertEquals(
            emptyList(),
            check(
                "package.json",
                "{\n  \"workspaces\": {\"packages\": [\"a/*\"]}\n}\n",
                "{\n  \"workspaces\": {\"packages\": [\"a/*\"]},\n  \"private\": true\n}\n",
            ),
            "Yarn classic's object form",
        )
    }

    @Test
    fun `a build file that can't be read before or after loosens nothing it could tell`() {
        assertEquals(emptyList(), check("package.json", "{\n  \"scripts\": {\"test\": \"jest\"}\n}\n", "{,\n"))
        assertEquals(emptyList(), check("tsconfig.json", "[]\n", "{\n}\n"))
        assertEquals(emptyList(), check("pnpm-workspace.yaml", "packages: [\n", "packages: []\n"))
        assertEquals(emptyList(), check("jest.config.js", "module.exports = { roots: [\"src\"] };\n", "module.exports = {};\n"))
    }

    @Test
    fun `settings that were off, references that name no path, and a workspace file without packages loosen nothing`() {
        assertEquals(emptyList(), check("tsconfig.json", "{\n  \"compilerOptions\": {\"strict\": false}\n}\n", "{\n}\n"))
        assertEquals(emptyList(), check("tsconfig.json", "{\n  \"references\": [\"./a\", {\"prepend\": true}]\n}\n", "{\n}\n"))
        assertEquals(emptyList(), check("pnpm-workspace.yaml", "onlyBuiltDependencies: [esbuild]\n", "packages: [42]\n"))
    }

    @Test
    fun `other build files have no blocklist`() {
        val before = "packages:\n  - \"packages/*\"\n"

        assertEquals(emptyList(), check("pnpm-workspace.yaml", before, "$before  - \"tools/*\"\nexclude: x\n"))
    }

    private fun check(
        file: String,
        before: String,
        after: String,
    ) = guardrails.check(listOf(FileChange(file, before, after, adapter.languageSupport)), emptyList())
}
