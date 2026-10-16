package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Fragment
import gmbh.vanella.lector.core.LineRange
import gmbh.vanella.lector.core.Method
import gmbh.vanella.lector.core.SourceFacts
import gmbh.vanella.lector.core.TestMethod
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The queries on the source fixtures under `fixtures/language-facts/`. */
class JvmLanguageSupportTest {
    @Test
    fun `java methods, test methods with their assertions, skips, suppressions, and stubs`() {
        assertEquals(
            SourceFacts(
                methods =
                    listOf(
                        Method(
                            "OrderTest",
                            "total",
                            lines(12, 18),
                            "Order order = new Order(2);\n        assertEquals(4, order.total(), \"total in €\");\n" +
                                "        assertThat(order.items())\n            .hasSize(2);",
                        ),
                        Method("OrderTest", "skipped", lines(20, 25), "assumeTrue(false);\n        assert order != null;"),
                        Method("OrderTest", "helper", lines(27, 30), "return null; // TODO: real value"),
                        Method("OrderTest", "empty", lines(32), ""),
                        Method("OrderTest.Order", "total", lines(39, 41), "return 4;"),
                    ),
                tests =
                    listOf(
                        TestMethod(
                            "OrderTest",
                            "total",
                            lines(12, 18),
                            listOf(
                                Fragment(lines(15), "assertEquals(4, order.total(), \"total in €\");"),
                                Fragment(lines(16, 17), "assertThat(order.items())\n            .hasSize(2);"),
                            ),
                        ),
                        TestMethod("OrderTest", "skipped", lines(20, 25), listOf(Fragment(lines(24), "assert order != null;"))),
                    ),
                skips = listOf(Fragment(lines(20), "@Disabled(\"flaky\")"), Fragment(lines(23), "assumeTrue(false)")),
                suppressions =
                    listOf(
                        Fragment(lines(27), "@SuppressWarnings(\"unchecked\")"),
                        Fragment(lines(34), "//noinspection unused"),
                    ),
                stubs =
                    listOf(
                        Fragment(lines(29), "return null;"),
                        Fragment(lines(29), "// TODO: real value"),
                        Fragment(lines(32), "{ }"),
                    ),
            ),
            facts("OrderTest.java"),
        )
    }

    @Test
    fun `kotlin functions, test functions with their assertions, skips, suppressions, and stubs`() {
        assertEquals(
            SourceFacts(
                methods =
                    listOf(
                        Method(
                            "OrderTest",
                            "total",
                            lines(12, 18),
                            "val order = Order(2)\n        assertEquals(4, order.total(), \"total in €\")\n" +
                                "        assertThat(order.items())\n            .hasSize(2)",
                        ),
                        Method("OrderTest", "skipped", lines(20, 24), "assumeTrue(false)"),
                        Method("OrderTest", "`expression body`", lines(26, 27), "assertEquals(4, Order(2).total())"),
                        Method("OrderTest", "helper", lines(29, 32), "return null // TODO: real value"),
                        Method("OrderTest", "empty", lines(34), ""),
                        Method("OrderTest", "later", lines(36), "TODO()"),
                        Method("OrderTest.Order", "total", lines(42), "items * 2"),
                        Method("Orders", "none", lines(47), "null"),
                    ),
                tests =
                    listOf(
                        TestMethod(
                            "OrderTest",
                            "total",
                            lines(12, 18),
                            listOf(
                                Fragment(lines(15), "assertEquals(4, order.total(), \"total in €\")"),
                                Fragment(lines(16, 17), "assertThat(order.items())\n            .hasSize(2)"),
                            ),
                        ),
                        TestMethod("OrderTest", "skipped", lines(20, 24), emptyList()),
                        TestMethod(
                            "OrderTest",
                            "`expression body`",
                            lines(26, 27),
                            listOf(Fragment(lines(27), "assertEquals(4, Order(2).total())")),
                        ),
                    ),
                skips = listOf(Fragment(lines(20), "@Disabled(\"flaky\")"), Fragment(lines(23), "assumeTrue(false)")),
                suppressions =
                    listOf(
                        Fragment(lines(1), "@file:Suppress(\"UNUSED\")"),
                        Fragment(lines(29), "@Suppress(\"UNCHECKED_CAST\")"),
                        Fragment(lines(38), "// noinspection unused"),
                    ),
                stubs =
                    listOf(
                        Fragment(lines(31), "return null"),
                        Fragment(lines(31), "// TODO: real value"),
                        Fragment(lines(34), "{}"),
                        Fragment(lines(36), "TODO()"),
                        Fragment(lines(47), "= null"),
                    ),
            ),
            facts("OrderTest.kt"),
        )
    }

    @Test
    fun `kotlin scripts are kotlin`() {
        assertEquals(listOf(Method("", "build", lines(1), "1")), JvmLanguageSupport.facts("build.gradle.kts", "fun build() = 1\n")?.methods)
    }

    @Test
    fun `a method without a body has an empty one`() {
        assertEquals(
            listOf(Method("A", "run", lines(2), "")),
            JvmLanguageSupport.facts("A.java", "abstract class A {\n    abstract void run();\n}\n")?.methods,
        )
        assertEquals(
            listOf(Method("A", "run", lines(2), "")),
            JvmLanguageSupport.facts("A.kt", "interface A {\n    fun run()\n}\n")?.methods,
        )
    }

    @Test
    fun `parse errors with their lines, in java and kotlin`() {
        val java = "class A {\n    void m() {\n        int x = ;\n    }\n}\n"
        val kotlin = "class A {\n    fun m() {\n        val x =\n    }\n}\n"

        assertEquals(listOf(lines(3)), JvmLanguageSupport.facts("A.java", java)?.parseErrors)
        assertEquals(listOf(lines(3)), JvmLanguageSupport.facts("A.kt", kotlin)?.parseErrors)
        assertEquals(listOf(lines(1)), JvmLanguageSupport.facts("A.java", "class A { int x = 1 }\n")?.parseErrors, "a missing `;`")
        assertEquals(emptyList(), JvmLanguageSupport.facts("A.java", "class A {}\n")?.parseErrors)
    }

    @Test
    fun `a file in no language here has no facts`() {
        assertNull(JvmLanguageSupport.facts("pom.xml", "<project/>"))
        assertNull(JvmLanguageSupport.facts("README.md", "# Java"))
    }

    private fun facts(fixture: String) =
        JvmLanguageSupport.facts(
            fixture,
            requireNotNull(javaClass.getResource("/fixtures/language-facts/$fixture")).toURI().toPath().readText(),
        )

    private fun lines(
        first: Int,
        last: Int = first,
    ) = LineRange(first, last)
}
