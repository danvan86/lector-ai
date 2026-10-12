package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DEFAULT_MAX_CHANGED_LINES
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.FileChange
import gmbh.vanella.lector.core.Guardrails
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.Violation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Every guardrail on real Java and Kotlin edits, through the Maven adapter: one bad edit each, and the edits that must pass. */
class JvmGuardrailsTest {
    private val adapter = MavenAdapter(plugins = MavenPlugins("rewrite", "help")) { _, _ -> error("no Maven here") }

    private val java =
        """
        package com.example;

        class OrderTest {
            @Test
            void total() {
                Order order = new Order(2);
                assertEquals(4, order.total());
            }

            @Test
            void items() {
                assertEquals(2, new Order(2).items());
            }

            int price(Order order) {
                return order.total() * 10;
            }

            Object label() {
                return null;
            }
        }
        """.trimIndent() + "\n"

    private val kotlin =
        """
        package com.example

        class OrderTest {
            @Test
            fun total() {
                val order = Order(2)
                assertEquals(4, order.total())
            }

            @Test
            fun items() {
                assertEquals(2, Order(2).items())
            }

            fun price(order: Order): Int {
                return order.total() * 10
            }

            fun label(): String? {
                return null
            }
        }
        """.trimIndent() + "\n"

    @Test
    fun `symbol comparison rejects a deleted method`() {
        assertEquals(listOf("symbol-comparison"), rules("OrderTest.java", java.without(PRICE_JAVA)))
        assertEquals(listOf("symbol-comparison"), rules("OrderTest.kt", kotlin.without(PRICE_KOTLIN)))
    }

    @Test
    fun `symbol comparison rejects a deleted method a current error points into, if its logic goes with it`() {
        val overrides = compileError(15, "method does not override or implement a method from a supertype")

        assertEquals(listOf("symbol-comparison"), rules("OrderTest.java", java.without(PRICE_JAVA), overrides))
        assertEquals(
            listOf("symbol-comparison"),
            rules("OrderTest.kt", kotlin.without(PRICE_KOTLIN), compileError(15, "'price' overrides nothing.", "OrderTest.kt")),
        )
    }

    @Test
    fun `symbol comparison lets a method go that a current error points into, if it was empty or its body moves`() {
        val filterJava = "class F implements Filter {\n    @Override\n    public void init(FilterConfig c) {}\n}\n"
        val filterKotlin = "class F : Filter {\n    override fun init(c: FilterConfig) {}\n}\n"
        val overrides = compileError(2, "method does not override or implement a method from a supertype", "F.java")

        assertEquals(
            emptyList(),
            violations("F.java", filterJava, filterJava.without("    @Override\n    public void init(FilterConfig c) {}\n"), overrides),
        )
        assertEquals(
            emptyList(),
            violations(
                "F.kt",
                filterKotlin,
                filterKotlin.without("    override fun init(c: FilterConfig) {}\n"),
                overrides.copy(file = "F.kt"),
            ),
        )
        assertEquals(emptyList(), rules("OrderTest.java", java.replace("int price(", "int cost("), compileError(15, "does not override")))
        assertEquals(
            emptyList(),
            rules(
                "OrderTest.kt",
                kotlin.replace("fun price(", "fun cost("),
                compileError(15, "'price' overrides nothing.", "OrderTest.kt"),
            ),
        )
    }

    @Test
    fun `test deletion and test count reject a deleted test, even one a diagnostic names`() {
        val diagnostic = compileError(12, "cannot find symbol: method items()")

        assertContains(rules("OrderTest.java", java.without(ITEMS_JAVA), diagnostic), "test-deletion")
        assertContains(rules("OrderTest.java", java.without(ITEMS_JAVA), diagnostic), "test-count")
        assertContains(rules("OrderTest.kt", kotlin.without(ITEMS_KOTLIN), diagnostic.copy(file = "OrderTest.kt")), "test-deletion")
        assertContains(rules("OrderTest.kt", kotlin.without(ITEMS_KOTLIN), diagnostic.copy(file = "OrderTest.kt")), "test-count")
    }

    @Test
    fun `test count rejects a test that is no test anymore`() {
        assertContains(rules("OrderTest.java", java.replaceFirst("    @Test\n    void items", "    void items")), "test-count")
        assertContains(rules("OrderTest.kt", kotlin.replaceFirst("    @Test\n    fun items", "    fun items")), "test-count")
    }

    @Test
    fun `test skips rejects a new Disabled or Ignore`() {
        assertEquals(
            listOf("test-skip"),
            rules("OrderTest.java", java.replaceFirst("    @Test\n    void items", "    @Disabled\n    @Test\n    void items")),
        )
        assertEquals(
            listOf("test-skip"),
            rules("OrderTest.kt", kotlin.replaceFirst("    @Test\n    fun items", "    @Ignore\n    @Test\n    fun items")),
        )
    }

    @Test
    fun `test assertions rejects a removed assertion`() {
        assertEquals(listOf("test-assertions"), rules("OrderTest.java", java.without("        assertEquals(4, order.total());\n")))
        assertEquals(listOf("test-assertions"), rules("OrderTest.kt", kotlin.without("        assertEquals(4, order.total())\n")))
    }

    @Test
    fun `test assertions rejects a changed expected value, unless a compile error points into the assertion`() {
        val javaChanged = java.replace("assertEquals(4, order.total())", "assertEquals(5, order.total())")
        val kotlinChanged = kotlin.replace("assertEquals(4, order.total())", "assertEquals(5, order.total())")

        assertEquals(listOf("test-assertions"), rules("OrderTest.java", javaChanged))
        assertEquals(listOf("test-assertions"), rules("OrderTest.kt", kotlinChanged))
        assertEquals(emptyList(), rules("OrderTest.java", javaChanged, compileError(7, "cannot find symbol: method total()")))
        assertEquals(emptyList(), rules("OrderTest.kt", kotlinChanged, compileError(7, "Unresolved reference 'total'.", "OrderTest.kt")))
    }

    @Test
    fun `test assertions lets a compile fix in an assertion pass, and a new assertion`() {
        val fix = java.replace("new Order(2).items()", "new Order(2).itemCount()")
        val added =
            kotlin.replace(
                "        assertEquals(4, order.total())\n",
                "        assertEquals(4, order.total())\n        assertEquals(2, order.items())\n",
            )

        assertEquals(emptyList(), rules("OrderTest.java", fix, compileError(12, "cannot find symbol: method items()")))
        assertEquals(emptyList(), rules("OrderTest.kt", added))
    }

    @Test
    fun `stubbing rejects a new return null, an emptied body, and a TODO`() {
        assertEquals(listOf("stubbing"), rules("OrderTest.java", java.replace("return order.total() * 10;", "return null;")))
        assertEquals(listOf("stubbing"), rules("OrderTest.java", java.replace("        return order.total() * 10;\n", "")))
        assertEquals(listOf("stubbing"), rules("OrderTest.java", java.replace("return order.total() * 10;", "return 0; // TODO")))
        assertEquals(listOf("stubbing"), rules("OrderTest.kt", kotlin.replace("return order.total() * 10", "return null")))
        assertEquals(listOf("stubbing"), rules("OrderTest.kt", kotlin.replace("        return order.total() * 10\n", "")))
        assertEquals(listOf("stubbing"), rules("OrderTest.kt", kotlin.replace("return order.total() * 10", "TODO()")))
    }

    @Test
    fun `stubbing lets an existing return null in an unchanged line pass`() {
        assertEquals(emptyList(), rules("OrderTest.java", java.replace("* 10;", "* 12;")))
        assertEquals(emptyList(), rules("OrderTest.kt", kotlin.replace("* 10", "* 12")))
    }

    @Test
    fun `stubbing and suppression let an existing placeholder stay in a changed line`() {
        val filter = "class F implements Filter {\n    public void init(javax.FilterConfig c) {}\n}\n"
        val todo = "class A {\n    void run() {\n        old(); // TODO remove later\n    }\n}\n"
        val listener = "class A : L {\n    override fun on(e: OldEvent) {}\n}\n"
        val suppressed = "class A {\n    @Suppress(\"DEPRECATION\") fun run(x: Old) = x.go()\n}\n"

        assertEquals(emptyList(), violations("F.java", filter, filter.replace("javax", "jakarta")))
        assertEquals(emptyList(), violations("A.java", todo, todo.replace("old()", "renamed()")))
        assertEquals(emptyList(), violations("A.kt", listener, listener.replace("OldEvent", "NewEvent")))
        assertEquals(emptyList(), violations("A.kt", suppressed, suppressed.replace("Old", "New")))
    }

    @Test
    fun `suppression rejects a new SuppressWarnings or Suppress`() {
        assertEquals(
            listOf("suppression"),
            rules("OrderTest.java", java.replace("    int price", "    @SuppressWarnings(\"unchecked\")\n    int price")),
        )
        assertEquals(
            listOf("suppression"),
            rules("OrderTest.kt", kotlin.replace("    fun price", "    @Suppress(\"UNCHECKED_CAST\")\n    fun price")),
        )
    }

    @Test
    fun `parse errors rejects an edit that breaks the syntax`() {
        assertEquals(listOf("parse-errors"), rules("OrderTest.java", java.replace("order.total() * 10;", "order.total() * ;")))
        assertEquals(listOf("parse-errors"), rules("OrderTest.kt", kotlin.replace("order.total() * 10", "order.total() *")))
    }

    @Test
    fun `the build-file blocklist rejects a new skipTests in the POM`() {
        val pom = "<project>\n  <properties>\n  </properties>\n</project>\n"

        assertEquals(
            listOf(Violation("build-file", "pom.xml", 3, "`skipTests` skips tests or ignores their failures")),
            violations("pom.xml", pom, pom.replace("  </properties>", "    <skipTests>true</skipTests>\n  </properties>")),
        )
    }

    @Test
    fun `the build-file blocklist rejects a new skipTests in the Maven config`() {
        assertEquals(
            listOf(Violation("build-file", ".mvn/maven.config", 2, "`skipTests` skips tests or ignores their failures")),
            violations(".mvn/maven.config", "-B\n", "-B\n-DskipTests\n"),
        )
    }

    @Test
    fun `diff size counts the lines of every file`() {
        val big =
            java.replace(
                "        return order.total() * 10;\n",
                (1..50).joinToString("") { "        int x$it = $it;\n" } + "        return x1;\n",
            )

        assertEquals(listOf("diff-size"), rules("OrderTest.java", big))
    }

    private fun rules(
        file: String,
        after: String,
        vararg diagnostics: Diagnostic,
    ) = violations(file, if (file.endsWith(".java")) java else kotlin, after, *diagnostics).map { it.rule }.distinct()

    private fun violations(
        file: String,
        before: String,
        after: String,
        vararg diagnostics: Diagnostic,
    ) = Guardrails(
        adapter,
        DEFAULT_MAX_CHANGED_LINES,
    ).check(listOf(FileChange(file, before, after, adapter.languageSupport)), diagnostics.toList())

    private fun compileError(
        line: Int,
        message: String,
        file: String = "OrderTest.java",
    ) = Diagnostic(file, line, 9, null, message, Severity.ERROR, "javac")

    private fun String.without(part: String): String {
        check(part in this) { "not in the source: $part" }
        return replace(part, "")
    }

    private companion object {
        const val PRICE_JAVA = "    int price(Order order) {\n        return order.total() * 10;\n    }\n\n"
        const val PRICE_KOTLIN = "    fun price(order: Order): Int {\n        return order.total() * 10\n    }\n\n"
        const val ITEMS_JAVA = "    @Test\n    void items() {\n        assertEquals(2, new Order(2).items());\n    }\n\n"
        const val ITEMS_KOTLIN = "    @Test\n    fun items() {\n        assertEquals(2, Order(2).items())\n    }\n\n"
    }
}
