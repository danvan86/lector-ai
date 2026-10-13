package gmbh.vanella.lector.ecosystems.shared

import gmbh.vanella.lector.core.Fragment
import gmbh.vanella.lector.core.LineRange
import gmbh.vanella.lector.core.Method
import gmbh.vanella.lector.core.TestMethod
import org.treesitter.TreeSitterJava
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The captures of the query files, read with the Java grammar and the Java queries in the test resources. */
class TreeSitterLanguageTest {
    private val java = TreeSitterLanguage("java", TreeSitterJava(), TreeSitterLanguageTest::class.java)

    @Test
    fun `methods with their types and bodies, tests with their assertions, skips, suppressions, and stubs`() {
        val facts =
            java.facts(
                """
                class Outer {
                    class Inner {
                        @Test
                        @Disabled
                        void works() {
                            assertThat(value()).isEqualTo("ü");
                        }

                        abstract void none();

                        @SuppressWarnings("x")
                        String value() { return null; }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                Method("Outer.Inner", "works", LineRange(3, 7), "assertThat(value()).isEqualTo(\"ü\");"),
                Method("Outer.Inner", "none", LineRange(9, 9), ""),
                Method("Outer.Inner", "value", LineRange(11, 12), "return null;"),
            ),
            facts.methods,
        )
        assertEquals(
            listOf(
                TestMethod(
                    "Outer.Inner",
                    "works",
                    LineRange(3, 7),
                    listOf(Fragment(LineRange(6, 6), "assertThat(value()).isEqualTo(\"ü\");")),
                ),
            ),
            facts.tests,
        )
        assertEquals(listOf(Fragment(LineRange(4, 4), "@Disabled")), facts.skips)
        assertEquals(listOf(Fragment(LineRange(11, 11), "@SuppressWarnings(\"x\")")), facts.suppressions)
        assertEquals(listOf(Fragment(LineRange(12, 12), "return null;")), facts.stubs)
        assertEquals(emptyList(), facts.parseErrors)
    }

    @Test
    fun `a method's type is only the types around it, and an assertion outside a statement is read on its own`() {
        val facts =
            java.facts(
                """
                class Before {}
                class Checks {
                    @Test
                    void asserts() {
                        assert ok();
                    }
                }
                class After {}
                """.trimIndent(),
            )

        assertEquals(listOf("Checks"), facts.methods.map { it.type })
        assertEquals(listOf(Fragment(LineRange(5, 5), "assert ok();")), facts.tests.single().assertions)
    }

    @Test
    fun `parse errors with their lines, for text the parser can't read and text it had to make up`() {
        assertEquals(listOf(LineRange(2, 2)), java.facts("class A {\n    void f( {\n}\n").parseErrors)
        assertEquals(listOf(LineRange(2, 2)), java.facts("class A {\n    int x = 1\n}\n").parseErrors)
    }

    @Test
    fun `a language without query files fails with the missing file`() {
        val error =
            assertFailsWith<IllegalStateException> { TreeSitterLanguage("cobol", TreeSitterJava(), TreeSitterLanguageTest::class.java) }

        assertEquals("No query file queries/cobol/types.scm", error.message)
    }
}
