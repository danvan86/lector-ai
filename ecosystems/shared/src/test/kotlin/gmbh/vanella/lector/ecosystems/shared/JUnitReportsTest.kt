package gmbh.vanella.lector.ecosystems.shared

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JUnitReportsTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `reads every test case with its failure or error, the first one if there are several`() {
        val report =
            report(
                """
                <testsuites name="jest tests" tests="4" failures="1" errors="0" time="1.786">
                  <testsuite name="src/calc.test.ts" errors="0" failures="1" skipped="0" tests="4">
                    <testcase classname="calc &gt; add" name="adds two numbers" time="0.002" file="src/calc.test.ts">
                    </testcase>
                    <testcase classname="calc &gt; add" name="fails on purpose" time="0.003" file="src/calc.test.ts">
                      <failure>Error: expect(received).toBe(expected) // Object.is equality

                Expected: 5
                Received: 4
                    at Object.&lt;anonymous&gt; (/work/src/calc.test.ts:10:25)</failure>
                    </testcase>
                    <testcase classname="Calc" name="breaks" time="0">
                      <error message="boom" type="java.lang.IllegalStateException">trace</error>
                      <failure message="later">ignored</failure>
                    </testcase>
                    <testcase classname="Calc" name="skipped"><skipped/></testcase>
                  </testsuite>
                </testsuites>
                """,
            )

        assertEquals(
            listOf(
                TestCase("calc > add", "adds two numbers", "src/calc.test.ts", null),
                TestCase(
                    "calc > add",
                    "fails on purpose",
                    "src/calc.test.ts",
                    TestFailure(
                        null,
                        null,
                        "Error: expect(received).toBe(expected) // Object.is equality\n\nExpected: 5\nReceived: 4\n" +
                            "    at Object.<anonymous> (/work/src/calc.test.ts:10:25)",
                    ),
                ),
                TestCase("Calc", "breaks", null, TestFailure("java.lang.IllegalStateException", "boom", "trace")),
                TestCase("Calc", "skipped", null, null),
            ),
            testCases(report),
        )
    }

    @Test
    fun `a report that isn't XML can't be read`() {
        val error = assertFailsWith<UnreadableReport> { testCases(report("<testsuite><testcase")) }

        // The parser's reason, in the JVM's language.
        assertEquals(error.cause?.message, error.message)
    }

    @Test
    fun `a report that isn't there can't be read`() {
        val error = assertFailsWith<UnreadableReport> { testCases(dir.resolve("missing.xml")) }

        assertContains(error.message.orEmpty(), "missing.xml")
    }

    @Test
    fun `a report with a DOCTYPE is not read, so no external entity is resolved`() {
        val report = report("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><testsuite>&e;</testsuite>""")

        val error = assertFailsWith<UnreadableReport> { testCases(report) }

        assertContains(error.message.orEmpty(), "DOCTYPE")
    }

    private fun report(xml: String) = dir.resolve("report.xml").also { it.writeText(xml.trimIndent()) }
}
