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

/**
 * Turns failed tests in surefire and failsafe XML reports (`TEST-<class>.xml` in `<module>/target/surefire-reports` and `failsafe-reports`) into [Diagnostic]s.
 * File and line come from the first stack frame inside the test class.
 */
internal object TestReports {
    private val reportPath = Regex("""^(?:(.+)/)?target/(surefire|failsafe)-reports/TEST-[^/]+\.xml$""")
    private val stackFrame = Regex("""^\s*at (?:\S+/)?([\w.$]+)\.[^.(]+\(([^:()]+):(\d+)\)""")

    fun parse(repo: Path): List<Diagnostic> {
        val reports =
            Files.walk(repo).use { paths ->
                paths
                    .asSequence()
                    .filter { it.isRegularFile() }
                    .mapNotNull { reportPath.matchEntire(relativeTo(repo, it)) }
                    .toList()
            }
        return reports.sortedBy { it.value }.flatMap { report ->
            val (module, tool) = report.destructured
            parseReport(repo, report.value, repo.resolve(module), tool)
        }
    }

    private fun parseReport(
        repo: Path,
        report: String,
        module: Path,
        tool: String,
    ): List<Diagnostic> =
        try {
            val testcases = xmlDocumentBuilder().parse(repo.resolve(report).toFile()).getElementsByTagName("testcase")
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
    ) = Diagnostic(null, null, null, null, "Can't read test report $report: ${e.message}", Severity.ERROR, "maven")

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
