package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.ecosystems.shared.TestCase
import gmbh.vanella.lector.ecosystems.shared.UnreadableReport
import gmbh.vanella.lector.ecosystems.shared.testCases
import java.nio.file.Path

/** How a test's name joins its `describe` blocks and its own name, the way Vitest's report writes it. */
internal const val NAME_SEPARATOR = " > "

/**
 * The failed tests in a JUnit report of Jest (`jest-junit`) or Vitest, from one package. A test is named
 * `<file>#<describe > … > name>`, its file relative to the repository root. jest-junit writes the describe blocks as
 * the class name and the file as an attribute; Vitest writes the file as the class name and the full name as the name.
 * Line and column come from the first place in the failure's text that is in the test file.
 */
internal object NodeTestReports {
    fun diagnostics(
        report: Path,
        pkg: NodePackage,
        runner: Runner,
    ): List<Diagnostic> =
        try {
            testCases(report).mapNotNull { failure(it, pkg, runner) }.distinctBy { it.test }
        } catch (e: UnreadableReport) {
            // A report that can't be read may hold a failure, so it must not look green.
            listOf(
                Diagnostic(
                    null,
                    null,
                    null,
                    null,
                    "Can't read the ${runner.bin} report of ${pkg.label}: ${e.message}",
                    Severity.ERROR,
                    runner.bin,
                ),
            )
        }

    /** The number of test cases in [report], or null if it can't be read. */
    fun count(report: Path): Int? =
        try {
            testCases(report).size
        } catch (_: UnreadableReport) {
            null
        }

    private fun failure(
        testCase: TestCase,
        pkg: NodePackage,
        runner: Runner,
    ): Diagnostic? {
        val failure = testCase.failure ?: return null
        val file = if (runner == Runner.JEST) testCase.file else testCase.classname
        val name =
            if (runner ==
                Runner.JEST
            ) {
                listOf(testCase.classname, testCase.name).filter { it.isNotEmpty() }
            } else {
                listOf(testCase.name)
            }
        val location = if (file == null) null else Regex(Regex.escape(file) + """:(\d+):(\d+)""").find(failure.text)
        val (line, column) = if (location == null) null to null else location.groupValues[1].toInt() to location.groupValues[2].toInt()
        val path = if (file == null) pkg.relative(PACKAGE_JSON) else pkg.relative(file)
        return Diagnostic(
            file = if (file == null) null else path,
            line = line,
            column = column,
            code = failure.type,
            message =
                failure.message ?: failure.text
                    .lines()
                    .firstOrNull { it.isNotBlank() }
                    ?.trim() ?: "Test failed",
            severity = Severity.ERROR,
            tool = runner.bin,
            test = "$path#${name.joinToString(NAME_SEPARATOR)}",
        )
    }
}
