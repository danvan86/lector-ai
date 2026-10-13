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
        )) {
            assertTrue(adapter.isBuildFile(file), file)
        }
        for (file in listOf("src/index.ts", "src/tsconfig.ts", "README.md", "package-lock.json", "jest.setup.ts", "babel.config.js")) {
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
                ) to "a change to the `test` script changes which tests run",
                before.replace("\"preset\": \"ts-jest\"", "\"preset\": \"ts-jest\",\n    \"testPathIgnorePatterns\": [\"slow\"]") to
                    "`testPathIgnorePatterns` leaves tests out",
                before.replace("\"preset\": \"ts-jest\"", "\"preset\": \"ts-jest\",\n    \"modulePathIgnorePatterns\": [\"x\"]") to
                    "`modulePathIgnorePatterns` leaves tests out",
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
            listOf("`testPathIgnorePatterns` leaves tests out"),
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
