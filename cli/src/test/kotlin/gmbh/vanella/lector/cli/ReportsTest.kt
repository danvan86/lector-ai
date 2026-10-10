package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.Verification
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportsTest {
    private val buildError =
        Diagnostic(null, null, null, null, "Could not resolve dependencies\ndependency: x:y:1.0", Severity.ERROR, "maven")
    private val compileError =
        Diagnostic("app/src/main/java/App.java", 8, 9, null, "cannot find symbol\nsymbol: class Strng", Severity.ERROR, "javac")
    private val oldBug =
        Diagnostic(
            "lib/src/test/java/com/example/OldTest.java",
            12,
            null,
            "java.lang.AssertionError",
            "expected <1> but was <2>",
            Severity.ERROR,
            "surefire",
            "com.example.OldTest#fails",
        )
    private val flaky = Diagnostic(null, null, null, null, "timed out", Severity.ERROR, "surefire", "com.example.SlowTest#waits")

    @Test
    fun `text report lists caused by the update first, with build errors in their own group`() {
        val verification =
            Verification(causedByUpdate = listOf(compileError, buildError), preExisting = listOf(oldBug), flaky = listOf(flaky))

        assertEquals(
            """
            Caused by the update (2)
              Build errors:
              - [maven] Could not resolve dependencies
                  dependency: x:y:1.0
              In the code:
              - [javac] app/src/main/java/App.java:8:9: cannot find symbol
                  symbol: class Strng

            Pre-existing (1)
              - [surefire] com.example.OldTest#fails at lib/src/test/java/com/example/OldTest.java:12: expected <1> but was <2>

            Flaky (1)
              - [surefire] com.example.SlowTest#waits: timed out

            Unattributed (0)
              none
            """.trimIndent(),
            TextReport.render(verification),
        )
    }

    @Test
    fun `text report of a clean run`() {
        assertEquals(
            """
            Caused by the update (0)
              none

            Pre-existing (0)
              none

            Flaky (0)
              none

            Unattributed (0)
              none
            """.trimIndent(),
            TextReport.render(Verification()),
        )
    }

    @Test
    fun `text report without build errors has no groups`() {
        assertEquals(
            "Caused by the update (1)\n  - [surefire] com.example.OldTest#fails at lib/src/test/java/com/example/OldTest.java:12: expected <1> but was <2>",
            TextReport
                .render(Verification(causedByUpdate = listOf(oldBug)))
                .lines()
                .take(2)
                .joinToString("\n"),
        )
    }

    @Test
    fun `a test failure without a location counts as in the code, not as a build error`() {
        assertEquals(
            """
            Caused by the update (2)
              Build errors:
              - [maven] Could not resolve dependencies
                  dependency: x:y:1.0
              In the code:
              - [surefire] com.example.SlowTest#waits: timed out
            """.trimIndent(),
            TextReport.render(Verification(causedByUpdate = listOf(flaky, buildError))).substringBefore("\n\n"),
        )
    }

    @Test
    fun `only build errors need no code group`() {
        assertEquals(
            "Caused by the update (1)\n  Build errors:\n  - [maven] Could not resolve dependencies\n      dependency: x:y:1.0",
            TextReport.render(Verification(causedByUpdate = listOf(buildError))).substringBefore("\n\n"),
        )
    }

    @Test
    fun `json report has the four lists and every field, nulls included`() {
        val json = JsonReport.render(Verification(causedByUpdate = listOf(buildError), unattributed = listOf(oldBug)))

        assertEquals(
            Json.parseToJsonElement(
                """
                {
                  "causedByUpdate": [
                    {"file": null, "line": null, "column": null, "code": null, "message": "Could not resolve dependencies\ndependency: x:y:1.0",
                     "severity": "error", "tool": "maven", "test": null}
                  ],
                  "preExisting": [],
                  "flaky": [],
                  "unattributed": [
                    {"file": "lib/src/test/java/com/example/OldTest.java", "line": 12, "column": null, "code": "java.lang.AssertionError",
                     "message": "expected <1> but was <2>", "severity": "error", "tool": "surefire", "test": "com.example.OldTest#fails"}
                  ]
                }
                """,
            ),
            Json.parseToJsonElement(json),
        )
    }

    @Test
    fun `json report is one line`() {
        assertEquals(1, JsonReport.render(Verification(causedByUpdate = listOf(compileError))).lines().size)
    }
}
