package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** `tsc --pretty false` output, copied from real runs of TypeScript 5.9 (7.0 prints the same). */
class TscOutputTest {
    private val root = Path.of("/work/repo")

    @Test
    fun `every error with file, line, column, code, and message, paths relative to the repository root`() {
        val diagnostics = TscOutput.parse(output("tsc-noemit.txt"), root, root.resolve("packages/app"))

        assertEquals(11, diagnostics.size)
        assertEquals(
            Diagnostic(
                "packages/app/src/a.ts",
                1,
                10,
                "TS2305",
                "Module '\"./b\"' has no exported member 'missing'.",
                Severity.ERROR,
                "tsc",
            ),
            diagnostics.first(),
        )
        assertEquals(
            Diagnostic(
                "packages/app/src/c.ts",
                5,
                7,
                "TS2322",
                "Type '{ name: number; age: number; }' is not assignable to type 'Q'.\n" +
                    "  Types of property 'name' are incompatible.\n" +
                    "    Type 'number' is not assignable to type 'string'.",
                Severity.ERROR,
                "tsc",
            ),
            diagnostics.last(),
            "an error's indented lines belong to its message",
        )
    }

    @Test
    fun `an error in a config file, and one without a location`() {
        assertEquals(
            listOf(Diagnostic("tsconfig.bad.json", 1, 21, "TS5023", "Unknown compiler option 'bogus'.", Severity.ERROR, "tsc")),
            TscOutput.parse(output("tsc-config-error.txt"), root, root),
        )
        assertEquals(
            listOf(
                Diagnostic(
                    null,
                    null,
                    null,
                    "TS18003",
                    "No inputs were found in config file '/work/empty/tsconfig.json'. Specified 'include' paths were '[\"nothing\"]' and " +
                        "'exclude' paths were '[]'.",
                    Severity.ERROR,
                    "tsc",
                ),
            ),
            TscOutput.parse(output("tsc-no-inputs.txt"), root, root),
        )
    }

    @Test
    fun `the package manager's lines around tsc's are ignored, and so are indented lines before the first error`() {
        val output =
            """
              stray indented line
            yarn run v1.22.22
            ${'$'} /work/repo/node_modules/.bin/tsc --noEmit --pretty false -p tsconfig.json
            src/a.ts(4,7): error TS2322: Type 'string' is not assignable to type 'number'.
            ${"   "}
            error Command failed with exit code 2.
            info Visit https://yarnpkg.com/en/docs/cli/run for documentation about this command.
            """.trimIndent()

        assertEquals(listOf("src/a.ts:4"), TscOutput.parse(output, root, root).map { "${it.file}:${it.line}" })
    }

    private fun output(name: String) = requireNotNull(javaClass.getResource("/outputs/$name")).readText()
}
