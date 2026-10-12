package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

/** The surefire and failsafe reports, `TEST-<class>.xml` in `<module>/target/surefire-reports` and `failsafe-reports`. */
internal val MAVEN_REPORTS =
    TestReports("maven", Regex("""^(?:(.+)/)?target/(surefire|failsafe)-reports/TEST-[^/]+\.xml$""")) { _, plugin -> plugin }

/**
 * Gradle's reports, `TEST-<class>.xml` in `<project>/build/test-results/<task>`, named by the path of the `Test` task that
 * wrote them, e.g. `:app:integrationTest`. The project path follows the directory, as in Gradle's default layout.
 */
internal val GRADLE_REPORTS =
    TestReports("gradle", Regex("""^(?:(.+)/)?build/test-results/([^/]+)/TEST-[^/]+\.xml$""")) { module, task ->
        if (module.isEmpty()) ":$task" else ":${module.replace('/', ':')}:$task"
    }

/**
 * Turns failed tests in JUnit XML reports into [Diagnostic]s. [layout] matches a report's path relative to the
 * repository: its first group is the module directory, its second what [tool] names the diagnostic by. File and line
 * come from the first stack frame inside the test class. [buildTool] names the problem of a report that can't be read.
 */
internal class TestReports(
    private val buildTool: String,
    private val layout: Regex,
    private val tool: (module: String, name: String) -> String,
) {
    private val stackFrame = Regex("""^\s*at (?:\S+/)?([\w.$]+)\.[^.(]+\(([^:()]+):(\d+)\)""")

    fun parse(repo: Path): List<Diagnostic> =
        reports(repo).flatMap { report ->
            val (module, name) = report.destructured
            parseReport(repo, report.value, repo.resolve(module), tool(module, name))
        }

    /** The number of test cases in the reports, or null if there are none or one can't be read. */
    fun count(repo: Path): Int? {
        val reports = reports(repo)
        return try {
            reports.sumOf { testcases(repo.resolve(it.value)).length }.takeIf { reports.isNotEmpty() }
        } catch (_: SAXException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun reports(repo: Path): List<MatchResult> =
        Files
            .walk(repo)
            .use { paths ->
                paths
                    .asSequence()
                    .filter { it.isRegularFile() }
                    .mapNotNull { layout.matchEntire(relativeTo(repo, it)) }
                    .toList()
            }.sortedBy { it.value }

    private fun testcases(report: Path) = xmlDocumentBuilder().parse(report.toFile()).getElementsByTagName("testcase")

    private fun parseReport(
        repo: Path,
        report: String,
        module: Path,
        tool: String,
    ): List<Diagnostic> =
        try {
            val testcases = testcases(repo.resolve(report))
            (0 until testcases.length)
                .map { testcases.item(it) as Element }
                .mapNotNull { failure(repo, module, tool, it) }
        } catch (e: SAXException) {
            listOf(unreadable(report, e))
        } catch (e: IOException) {
            listOf(unreadable(report, e))
        }

    // A report that can't be read may hold a failure, so it must not look green.
    private fun unreadable(
        report: String,
        e: Exception,
    ) = Diagnostic(null, null, null, null, "Can't read test report $report: ${e.message}", Severity.ERROR, buildTool)

    private fun failure(
        repo: Path,
        module: Path,
        tool: String,
        testcase: Element,
    ): Diagnostic? {
        val problem = testcase.childElements().firstOrNull { it.tagName == "failure" || it.tagName == "error" } ?: return null
        val className = testcase.getAttribute("classname")
        val trace = problem.textContent.trim()
        val location =
            trace
                .lineSequence()
                .mapNotNull { stackFrame.find(it)?.destructured }
                .firstOrNull { (frameClass) -> frameClass == className || frameClass.startsWith("$className$") }
                ?.let { (_, fileName, line) -> sourceFile(module, className, fileName)?.let { relativeTo(repo, it) to line.toInt() } }
        return Diagnostic(
            file = location?.first,
            line = location?.second,
            column = null,
            code = problem.getAttribute("type").ifEmpty { null },
            message = problem.getAttribute("message").ifEmpty { trace.lines().first() }.ifEmpty { "Test failed" },
            severity = Severity.ERROR,
            tool = tool,
            test = "$className#${testcase.getAttribute("name")}",
        )
    }

    /** The test's source file in the standard layout, `<module>/src/<source set>/<language>/<package>/<file>`. */
    private fun sourceFile(
        module: Path,
        className: String,
        fileName: String,
    ): Path? {
        val src = module.resolve("src")
        if (!src.isDirectory()) return null
        val packagePath = className.substringBeforeLast('.', "").replace('.', '/')
        return Files.walk(src, 2).use { roots ->
            roots
                .asSequence()
                .map { it.resolve(packagePath).resolve(fileName) }
                .filter { it.isRegularFile() }
                .sorted()
                .firstOrNull()
        }
    }

    private fun Element.childElements() = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()
}
