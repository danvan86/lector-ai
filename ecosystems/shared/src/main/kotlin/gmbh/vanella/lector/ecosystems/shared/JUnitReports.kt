package gmbh.vanella.lector.ecosystems.shared

import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.IOException
import java.nio.file.Path

/**
 * One `<testcase>` of a JUnit XML report. [file] is its `file` attribute, if the runner writes one. [failure] is its
 * first `<failure>` or `<error>`, null if it passed or was skipped.
 */
data class TestCase(
    val classname: String,
    val name: String,
    val file: String?,
    val failure: TestFailure?,
)

/** Why a test case failed: its `type` and `message` attributes, null if empty, and its text, usually a stack trace. */
data class TestFailure(
    val type: String?,
    val message: String?,
    val text: String,
)

/** A report that can't be read, with the parser's or the file system's reason. It may hold a failure, so it must never look green. */
class UnreadableReport(
    cause: Exception,
) : Exception(cause.message, cause)

/**
 * The test cases of the JUnit XML [report], the format that surefire, Gradle, jest-junit, and Vitest write. Fails with
 * [UnreadableReport] if the file can't be opened or isn't such a report.
 */
fun testCases(report: Path): List<TestCase> {
    val testcases =
        try {
            xmlDocumentBuilder().parse(report.toFile()).getElementsByTagName("testcase")
        } catch (e: SAXException) {
            throw UnreadableReport(e)
        } catch (e: IOException) {
            throw UnreadableReport(e)
        }
    return (0 until testcases.length).map { testCase(testcases.item(it) as Element) }
}

private fun testCase(element: Element): TestCase {
    val problem = element.childElements().firstOrNull { it.tagName == "failure" || it.tagName == "error" }
    return TestCase(
        classname = element.getAttribute("classname"),
        name = element.getAttribute("name"),
        file = element.getAttribute("file").ifEmpty { null },
        failure =
            problem?.let {
                TestFailure(it.getAttribute("type").ifEmpty { null }, it.getAttribute("message").ifEmpty { null }, it.textContent.trim())
            },
    )
}

private fun Element.childElements() = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()
