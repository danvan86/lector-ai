package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reports copied from real runs: jest-junit 17 with Jest 30 and ts-jest, and Vitest 4. */
class NodeTestReportsTest {
    @TempDir
    lateinit var dir: Path

    private val app = NodePackage("packages/app", PackageJson(null, emptyMap(), emptyMap(), emptyList(), null, null), true)
    private val root = app.copy(dir = "")

    @Test
    fun `jest failures with file, line, and the describe path, and a suite that failed to run, once`() {
        val diagnostics = NodeTestReports.diagnostics(output("jest-junit.xml"), root, Runner.JEST)

        assertEquals(
            listOf(
                Diagnostic(
                    "src/calc.test.ts",
                    10,
                    25,
                    null,
                    "Error: expect(received).toBe(expected) // Object.is equality",
                    Severity.ERROR,
                    "jest",
                    "src/calc.test.ts#calc > add > fails on purpose",
                ),
                Diagnostic(
                    "src/broken.test.ts",
                    1,
                    25,
                    null,
                    "● Test suite failed to run",
                    Severity.ERROR,
                    "jest",
                    "src/broken.test.ts#Test suite failed to run > src/broken.test.ts",
                ),
            ),
            diagnostics,
        )
        assertEquals(5, NodeTestReports.count(output("jest-junit.xml")))
    }

    @Test
    fun `vitest failures in a workspace package, paths relative to the repository root`() {
        assertEquals(
            listOf(
                Diagnostic(
                    "packages/app/src/welcome.test.ts",
                    10,
                    28,
                    "AssertionError",
                    "expected 'HELLO, BOB!' to be 'hello, bob!' // Object.is equality",
                    Severity.ERROR,
                    "vitest",
                    "packages/app/src/welcome.test.ts#welcome > fails on purpose",
                ),
            ),
            NodeTestReports.diagnostics(output("vitest-junit.xml"), app, Runner.VITEST),
        )
    }

    @Test
    fun `a failure without file, location, or text still is a failure of the package`() {
        val report = dir.resolve("report.xml")
        report.writeText("""<testsuite><testcase classname="" name="works"><failure/></testcase></testsuite>""")

        assertEquals(
            listOf(Diagnostic(null, null, null, null, "Test failed", Severity.ERROR, "jest", "packages/app/package.json#works")),
            NodeTestReports.diagnostics(report, app, Runner.JEST),
        )
    }

    @Test
    fun `a report that can't be read is a build problem of the package, and counts nothing`() {
        val report = dir.resolve("report.xml")
        report.writeText("<testsuite")

        val problem = NodeTestReports.diagnostics(report, app, Runner.VITEST).single()

        assertEquals(null, problem.test)
        assertEquals("vitest", problem.tool)
        assertTrue(problem.message.startsWith("Can't read the vitest report of packages/app: "), problem.message)
        assertEquals(null, NodeTestReports.count(report))
    }

    private fun output(name: String) = requireNotNull(javaClass.getResource("/outputs/$name")).toURI().toPath()
}
