package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.Fragment
import gmbh.vanella.lector.core.LineRange
import gmbh.vanella.lector.core.Method
import gmbh.vanella.lector.core.TestMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Every query category for a TypeScript, a TSX, and a JavaScript sample (AC33), skip markers (AC34), suppressions (AC35). */
class TypeScriptLanguageSupportTest {
    @Test
    fun `typescript - functions, methods, exported arrow functions, tests by describe, assertions, skips, suppressions, stubs`() {
        val facts = facts("calc.test.ts")

        assertEquals(
            listOf(
                Method("", "double", LineRange(5, 7), "return add(x, x);"),
                Method("", "triple", LineRange(9, 9), "add(x, double(x))"),
                Method("", "nothing", LineRange(11, 13), "return null;"),
                Method("Shape", "area", LineRange(16, 16), ""),
                Method("Shape", "describe", LineRange(17, 20), "// TODO describe the shape\n    return \"shape\";"),
                Method("Shape", "reset", LineRange(21, 21), ""),
            ),
            facts.methods,
        )
        assertEquals(
            listOf(
                TestMethod(
                    "calc.with %i",
                    "adds",
                    LineRange(26, 29),
                    listOf(fragment(27, "expect(add(n, 1)).toBe(n + 1);"), fragment(28, "assert.ok(true);")),
                ),
                TestMethod(
                    "calc.with %i",
                    "doubles",
                    LineRange(30, 32),
                    listOf(fragment(31, "await expect(Promise.resolve(double(n))).resolves.toBe(2 * n);")),
                ),
                TestMethod("calc", "skipped", LineRange(34, 34), emptyList()),
                TestMethod("calc", "later", LineRange(35, 35), emptyList()),
                TestMethod("calc", "off", LineRange(36, 36), emptyList()),
                TestMethod("calc", "`template \${1}`", LineRange(37, 40), listOf(fragment(39, "expect(value).toBeDefined();"))),
            ),
            facts.tests,
        )
        assertEquals(
            listOf(
                fragment(30, "it.only"),
                fragment(34, "test.skip"),
                fragment(35, "it.todo"),
                fragment(36, "xit"),
                fragment(43, "xdescribe"),
            ),
            facts.skips,
        )
        assertEquals(
            listOf(fragment(1, "// @ts-ignore"), fragment(4, "/* eslint-disable no-console */"), fragment(38, "// @ts-expect-error")),
            facts.suppressions,
            "`as any` on line 38 is no suppression",
        )
        assertEquals(listOf(fragment(12, "return null;"), fragment(18, "// TODO describe the shape"), fragment(21, "{}")), facts.stubs)
        assertEquals(emptyList(), facts.parseErrors)
    }

    @Test
    fun `tsx - with its own grammar`() {
        val facts = facts("Button.test.tsx")

        assertEquals(
            listOf(Method("", "Button", LineRange(2, 4), "return <button>{label}</button>;"), Method("", "Empty", LineRange(6, 6), "")),
            facts.methods,
        )
        assertEquals(
            listOf(
                TestMethod("Button", "renders", LineRange(9, 11), listOf(fragment(10, "expect(<Button label=\"ok\" />).toBeTruthy();"))),
                TestMethod("Button", "is skipped", LineRange(12, 14), emptyList()),
            ),
            facts.tests,
        )
        assertEquals(listOf(fragment(12, "it.skip")), facts.skips)
        assertEquals(listOf(fragment(1, "// @ts-nocheck")), facts.suppressions)
        assertEquals(listOf(fragment(6, "{}"), fragment(13, "return undefined;")), facts.stubs)
        assertEquals(emptyList(), facts.parseErrors)
    }

    @Test
    fun `javascript - with its own grammar and queries`() {
        val facts = facts("util.test.js")

        assertEquals(
            listOf(
                Method("", "check", LineRange(2, 4), "return undefined;"),
                Method("Store", "get", LineRange(7, 7), ""),
                Method("Store", "clear", LineRange(8, 8), ""),
            ),
            facts.methods,
        )
        assertEquals(
            listOf(
                TestMethod("util", "checks", LineRange(12, 14), listOf(fragment(13, "expect(check(1)).toBeUndefined();"))),
                TestMethod("util", "off", LineRange(15, 15), emptyList()),
            ),
            facts.tests,
        )
        assertEquals(listOf(fragment(15, "xtest")), facts.skips)
        assertEquals(listOf(fragment(1, "// eslint-disable-next-line no-unused-vars")), facts.suppressions)
        assertEquals(listOf(fragment(3, "return undefined;"), fragment(7, "{}"), fragment(8, "{}")), facts.stubs)
        assertEquals(emptyList(), facts.parseErrors)
    }

    @Test
    fun `every way to skip a test, focus one, or expect it to fail is a skip marker, in each grammar (AC34)`() {
        val source =
            """
            fit("a", () => {});
            fdescribe("b", () => {});
            it.failing("c", () => {});
            test.fails("d", () => {});
            test.skipIf(true)("e", () => {});
            describe.runIf(false)("f", () => {});
            it.concurrent.skip("g", () => {});
            describe.sequential.only("h", () => {});
            it.skip.each([1])("i %i", () => {});
            it.each([1])("j %i", () => {});
            """.trimIndent() + "\n"
        val markers =
            listOf(
                "fit",
                "fdescribe",
                "it.failing",
                "test.fails",
                "test.skipIf",
                "describe.runIf",
                "it.concurrent.skip",
                "describe.sequential.only",
                "it.skip",
            )

        for (file in listOf("a.test.ts", "a.test.tsx", "a.test.js")) {
            assertEquals(markers, requireNotNull(TypeScriptLanguageSupport.facts(file, source)).skips.map { it.text }, file)
        }
    }

    @Test
    fun `parse errors in each grammar, and every extension in its language`() {
        val broken = "export function f( {\n  return 1;\n}\n"

        for (file in listOf("a.ts", "a.mts", "a.cts", "a.tsx", "a.js", "a.jsx", "a.mjs", "a.cjs")) {
            assertEquals(1, requireNotNull(TypeScriptLanguageSupport.facts(file, broken)).parseErrors.first().first, file)
        }
        assertEquals(
            emptyList(),
            requireNotNull(TypeScriptLanguageSupport.facts("a.tsx", "export const A = () => <div>{1}</div>;\n")).parseErrors,
        )
    }

    @Test
    fun `files in no language here have no facts`() {
        assertNull(TypeScriptLanguageSupport.facts("package.json", "{}"))
        assertNull(TypeScriptLanguageSupport.facts("README.md", "# TypeScript"))
        assertNull(TypeScriptLanguageSupport.facts("index.d.ts.map", "{}"))
    }

    private fun facts(name: String) =
        requireNotNull(TypeScriptLanguageSupport.facts(name, requireNotNull(javaClass.getResource("/language-facts/$name")).readText()))

    private fun fragment(
        line: Int,
        text: String,
    ) = Fragment(LineRange(line, line), text)
}
