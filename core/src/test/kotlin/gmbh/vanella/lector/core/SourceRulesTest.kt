package gmbh.vanella.lector.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** The source rules on facts given by hand. The same rules on real Java and Kotlin: `JvmGuardrailsTest` in `ecosystems:jvm`. */
class SourceRulesTest {
    private val run = Method("App", "run", lines(2, 4), "go();")
    private val stop = Method("App", "stop", lines(5, 7), "halt();\n    log();")

    @Test
    fun `symbol comparison rejects a deleted method, an overload included, and one moved to another type`() {
        val deleted = check(SymbolComparison, SourceFacts(methods = listOf(run, stop)), SourceFacts(methods = listOf(run)))
        val overload =
            check(SymbolComparison, SourceFacts(methods = listOf(run, run.copy(lines = lines(8, 9)))), SourceFacts(methods = listOf(run)))
        val moved = check(SymbolComparison, SourceFacts(methods = listOf(run)), SourceFacts(methods = listOf(run.copy(type = "Other"))))

        assertEquals(
            listOf(
                Violation(
                    "symbol-comparison",
                    "App.java",
                    5,
                    "the edits delete the method `App.stop` (line 5 before the edits) and its logic. A method may only go if a " +
                        "current error points into it, and its body is empty or moves to a method the edits add",
                ),
            ),
            deleted,
        )
        assertEquals(listOf(8), overload.map { it.line })
        assertEquals(1, moved.size)
    }

    @Test
    fun `symbol comparison rejects a deleted method a current error points into, if its logic goes with it`() {
        val before = SourceFacts(methods = listOf(run, stop))

        assertEquals(1, check(SymbolComparison, before, SourceFacts(methods = listOf(run)), listOf(error(6, "does not override"))).size)
    }

    @Test
    fun `symbol comparison lets an empty method go, or a renamed or moved one with its body, if a current error points into it`() {
        val empty = stop.copy(body = "")
        val renamed = stop.copy(name = "halt", body = "halt();\n        log();")
        val moved = stop.copy(type = "Other")
        val overrides = listOf(error(5, "method does not override or implement a method from a supertype"))

        assertEquals(
            emptyList(),
            check(SymbolComparison, SourceFacts(methods = listOf(run, empty)), SourceFacts(methods = listOf(run)), overrides),
        )
        assertEquals(
            emptyList(),
            check(SymbolComparison, SourceFacts(methods = listOf(run, stop)), SourceFacts(methods = listOf(run, renamed)), overrides),
        )
        assertEquals(
            emptyList(),
            check(SymbolComparison, SourceFacts(methods = listOf(run, stop)), SourceFacts(methods = listOf(run, moved)), overrides),
        )
    }

    @Test
    fun `symbol comparison counts only an error in the method's own file and lines, not one that names it`() {
        val before = SourceFacts(methods = listOf(run, stop.copy(body = "")))
        val after = SourceFacts(methods = listOf(run))

        assertEquals(1, check(SymbolComparison, before, after, listOf(error(1, "method stop() is never used"))).size, "outside the method")
        assertEquals(
            1,
            check(SymbolComparison, before, after, listOf(error(6, "cannot find symbol: method stop()").copy(file = "Other.java"))).size,
            "in another file",
        )
        assertEquals(1, check(SymbolComparison, before, after, listOf(error(6, "no line").copy(line = null))).size, "no line")
    }

    @Test
    fun `symbol comparison names a top-level function without a type`() {
        val violations = check(SymbolComparison, SourceFacts(methods = listOf(Method("", "main", lines(1), "run()"))), SourceFacts())

        assertEquals(
            "the edits delete the method `main` (line 1 before the edits) and its logic. A method may only go if a " +
                "current error points into it, and its body is empty or moves to a method the edits add",
            violations.single().reason,
        )
    }

    @Test
    fun `test deletion rejects a deleted test, even if a diagnostic names it`() {
        val before = SourceFacts(tests = listOf(test("total"), test("count")))
        val after = SourceFacts(tests = listOf(test("total")))

        assertEquals(
            listOf(
                Violation(
                    "test-deletion",
                    "App.java",
                    2,
                    "the edits delete the test `AppTest.count` (line 2 before the edits). Tests are never deleted: make them compile and pass instead",
                ),
            ),
            check(TestDeletion, before, after, listOf(error(3, "cannot find symbol count"))),
        )
    }

    @Test
    fun `test skips rejects a new skip marker, and lets existing ones move`() {
        val disabled = Fragment(lines(3), "@Disabled")
        val before = SourceFacts(skips = listOf(disabled))

        assertEquals(
            listOf(Violation("test-skip", "App.java", 9, "the edits add `@Disabled`, which skips tests")),
            check(TestSkips, before, SourceFacts(skips = listOf(disabled.copy(lines = lines(4)), Fragment(lines(9), "@Disabled")))),
        )
        assertEquals(emptyList(), check(TestSkips, before, SourceFacts(skips = listOf(disabled.copy(lines = lines(5))))))
    }

    @Test
    fun `test assertions rejects a test with fewer assertions, once`() {
        val before = SourceFacts(tests = listOf(test("total", "assertEquals(4, total())", "assertNotNull(order)")))
        val after = SourceFacts(tests = listOf(test("total", "assertEquals(4, total())")))

        assertEquals(
            listOf(Violation("test-assertions", "App.java", 2, "the test `AppTest.total` has 1 assertions after the edits, 2 before")),
            check(TestAssertions, before, after),
        )
    }

    @Test
    fun `test assertions rejects a changed assertion that no compile error points into`() {
        val before = SourceFacts(tests = listOf(test("total", "assertEquals(4, total())")))
        val after = SourceFacts(tests = listOf(test("total", "assertEquals(5, total())")))
        val testFailure = Diagnostic("App.java", 3, null, null, "expected 4", Severity.ERROR, "surefire", "AppTest#total")
        val elsewhere = Diagnostic("Other.java", 3, null, null, "cannot find symbol", Severity.ERROR, "javac")
        val noLine = Diagnostic("App.java", null, null, null, "cannot find symbol", Severity.ERROR, "javac")

        assertEquals(
            listOf(
                Violation(
                    "test-assertions",
                    "App.java",
                    3,
                    "the edits change the assertion `assertEquals(4, total())` in the test `AppTest.total` (line 3 before the edits), " +
                        "and no compile error points into it. A failing test is fixed in the code it tests, not in what it expects",
                ),
            ),
            check(TestAssertions, before, after, listOf(testFailure, elsewhere, noLine)),
        )
    }

    @Test
    fun `test assertions lets a compile fix inside an assertion pass, and new or re-indented assertions`() {
        val before = SourceFacts(tests = listOf(test("total", "assertEquals(200, response.getStatusCodeValue())")))
        val fixed = SourceFacts(tests = listOf(test("total", "assertEquals(200, response.getStatusCode().value())")))
        val compileError = Diagnostic("App.java", 3, 30, null, "cannot find symbol getStatusCodeValue", Severity.ERROR, "javac")
        val added =
            SourceFacts(tests = listOf(test("total", "assertEquals(200,\n    response.getStatusCodeValue())", "assertNotNull(response)")))

        assertEquals(emptyList(), check(TestAssertions, before, fixed, listOf(compileError)))
        assertEquals(emptyList(), check(TestAssertions, before, added))
    }

    @Test
    fun `test assertions leaves a deleted test to test deletion`() {
        assertEquals(emptyList(), check(TestAssertions, SourceFacts(tests = listOf(test("total", "assertTrue(ok)"))), SourceFacts()))
    }

    @Test
    fun `test count rejects fewer test methods over all changed files`() {
        val before = SourceFacts(tests = listOf(test("total"), test("count")))

        assertEquals(
            listOf(Violation("test-count", null, null, "the changed files have 1 test methods after the edits, 2 before")),
            check(TestCount, before, SourceFacts(tests = listOf(test("total")))),
        )
        assertEquals(emptyList(), check(TestCount, before, SourceFacts(tests = listOf(test("total"), test("renamed")))))
    }

    @Test
    fun `stubbing rejects a placeholder in a changed line, and not one in an unchanged line`() {
        val before = "Object a() {\n    return null;\n}\nObject b() {\n    return b;\n}\n"
        val after = "Object a() {\n    return null;\n}\nObject b() {\n    return null;\n}\n"
        val stubs = SourceFacts(stubs = listOf(Fragment(lines(2), "return null;"), Fragment(lines(5), "return null;")))

        assertEquals(
            listOf(Violation("stubbing", "App.java", 5, "the edits add the placeholder `return null;` in place of logic")),
            Stubbing.check(attempt(before, after, SourceFacts(stubs = stubs.stubs.take(1)), stubs)),
        )
    }

    @Test
    fun `suppression rejects a suppression in a changed line, and not one in an unchanged line`() {
        val before = "@SuppressWarnings(\"a\")\nvoid a() {}\nvoid b() {}\n"
        val after = "@SuppressWarnings(\"a\")\nvoid a() {}\n@SuppressWarnings(\"b\")\nvoid b() {}\n"
        val existing = Fragment(lines(1), "@SuppressWarnings(\"a\")")

        assertEquals(
            listOf(Violation("suppression", "App.java", 3, "the edits add the suppression `@SuppressWarnings(\"b\")`")),
            Suppression.check(
                attempt(
                    before,
                    after,
                    SourceFacts(suppressions = listOf(existing)),
                    SourceFacts(suppressions = listOf(existing, Fragment(lines(3), "@SuppressWarnings(\"b\")"))),
                ),
            ),
        )
    }

    @Test
    fun `stubbing and suppression let a placeholder stay in a line the edits changed around it`() {
        val before = "@Suppress(\"X\") fun a(e: Old) {}\n"
        val after = "@Suppress(\"X\") fun a(e: New) {}\n"
        val facts = SourceFacts(stubs = listOf(Fragment(lines(1), "{}")), suppressions = listOf(Fragment(lines(1), "@Suppress(\"X\")")))
        val emptied = SourceFacts(stubs = listOf(Fragment(lines(1), "{ }")), suppressions = facts.suppressions)

        assertEquals(emptyList(), Stubbing.check(attempt(before, after, facts, facts)))
        assertEquals(emptyList(), Suppression.check(attempt(before, after, facts, facts)))
        assertEquals(
            listOf(1),
            Stubbing.check(attempt(before, after, facts, SourceFacts(stubs = facts.stubs + emptied.stubs))).map { it.line },
        )
    }

    @Test
    fun `parse errors rejects more parse errors than before, at the first one the edits touched`() {
        val before = "class A {\n  int x = ;\n}\n"
        val after = "class A {\n  int x = ;\n  int y = ;\n}\n"
        val old = SourceFacts(parseErrors = listOf(lines(2)))
        val more = SourceFacts(parseErrors = listOf(lines(2), lines(3)))

        assertEquals(
            listOf(Violation("parse-errors", "App.java", 3, "the file doesn't parse: 2 parse errors after the edits, 1 before")),
            ParseErrors.check(attempt(before, after, old, more)),
        )
        assertEquals(
            listOf(2),
            ParseErrors.check(attempt(before, "class A {\n  int x = ;\n}\n// end\n", old, more)).map {
                it.line
            },
            "none touched: the first",
        )
        assertEquals(emptyList(), ParseErrors.check(attempt(before, after, old, old)))
    }

    @Test
    fun `files that no language knows have no source facts to check`() {
        val pom = FileChange("pom.xml", "<a/>\n", "<b/>\n") { _, _ -> null }
        val halfKnown =
            FileChange("App.java", "a\n", "b\n") { _, content ->
                if (content ==
                    "a\n"
                ) {
                    SourceFacts(methods = listOf(run))
                } else {
                    null
                }
            }

        assertEquals(emptyList(), SymbolComparison.check(AttemptChanges(listOf(pom, halfKnown), emptyList())))
    }

    @Test
    fun `the guardrails run every rule and report every violation`() {
        val change =
            FileChange("App.java", "before\n", "after\n") { _, content ->
                if (content ==
                    "before\n"
                ) {
                    SourceFacts(methods = listOf(run), tests = listOf(test("total")))
                } else {
                    SourceFacts()
                }
            }

        val violations = Guardrails(EditableEverywhere, maxChangedLines = 1).check(listOf(change), emptyList())

        assertEquals(listOf("diff-size", "symbol-comparison", "test-deletion", "test-count"), violations.map { it.rule })
    }

    private fun check(
        rule: Guardrail,
        before: SourceFacts,
        after: SourceFacts,
        diagnostics: List<Diagnostic> = emptyList(),
    ) = rule.check(attempt("before\n", "after\n", before, after, diagnostics))

    private fun attempt(
        before: String,
        after: String,
        factsBefore: SourceFacts,
        factsAfter: SourceFacts,
        diagnostics: List<Diagnostic> = emptyList(),
    ) = AttemptChanges(
        listOf(FileChange("App.java", before, after) { _, content -> if (content == before) factsBefore else factsAfter }),
        diagnostics,
    )

    private fun test(
        name: String,
        vararg assertions: String,
    ) = TestMethod("AppTest", name, lines(2, 6), assertions.mapIndexed { index, text -> Fragment(lines(3 + index), text) })

    private fun error(
        line: Int,
        message: String,
    ) = Diagnostic("App.java", line, 1, null, message, Severity.ERROR, "javac")

    private fun lines(
        first: Int,
        last: Int = first,
    ) = LineRange(first, last)
}
