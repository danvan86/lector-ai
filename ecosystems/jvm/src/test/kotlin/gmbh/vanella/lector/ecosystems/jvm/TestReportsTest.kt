package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TestReportsTest {
    @TempDir
    lateinit var repo: Path

    @Test
    fun `surefire and failsafe failures in a multi-module build, with test file and line`() {
        val fixture = requireNotNull(javaClass.getResource("/fixtures/failing-tests")).toURI().toPath()

        assertEquals(
            listOf(
                Diagnostic(
                    "app/src/test/java/com/example/app/GreetingIT.java",
                    10,
                    null,
                    "org.opentest4j.AssertionFailedError",
                    "expected: <Hello, world> but was: <Hello>",
                    Severity.ERROR,
                    "failsafe",
                    "com.example.app.GreetingIT#greets",
                ),
                Diagnostic(
                    "lib/src/test/java/com/example/lib/CalculatorTest.java",
                    17,
                    null,
                    "org.opentest4j.AssertionFailedError",
                    "expected: <5> but was: <4>",
                    Severity.ERROR,
                    "surefire",
                    "com.example.lib.CalculatorTest#addsWrong",
                ),
                Diagnostic(
                    "lib/src/test/java/com/example/lib/CalculatorTest.java",
                    22,
                    null,
                    "java.lang.ArithmeticException",
                    "/ by zero",
                    Severity.ERROR,
                    "surefire",
                    "com.example.lib.CalculatorTest#dividesByZero",
                ),
            ),
            TestReports.parse(fixture),
        )
    }

    @Test
    fun `a repository without reports has no test failures`() {
        assertEquals(emptyList(), TestReports.parse(repo))
    }

    @Test
    fun `the location comes from the first frame in the test class, also for nested classes and lambdas`() {
        source("src/test/kotlin/com/example/FooTest.kt")
        report(
            """
            <testcase name="bar" classname="com.example.FooTest${'$'}Inner">
              <failure type="java.lang.AssertionError"><![CDATA[java.lang.AssertionError
            	at org.junit.Assert.fail(Assert.java:89)
            	at com.example.FooTest${'$'}Inner.bar${'$'}lambda${'$'}0(FooTest.kt:7)
            	at com.example.FooTest${'$'}Inner.bar(FooTest.kt:12)
            ]]></failure>
            </testcase>
            """,
        )

        val failure = TestReports.parse(repo).single()

        assertEquals("src/test/kotlin/com/example/FooTest.kt", failure.file)
        assertEquals(7, failure.line)
        assertEquals("java.lang.AssertionError", failure.message)
        assertEquals("com.example.FooTest\$Inner#bar", failure.test)
    }

    @Test
    fun `without a frame in the test class there is no location`() {
        report(
            """
            <testcase name="bar" classname="com.example.FooTest">
              <error message="timed out" type="java.util.concurrent.TimeoutException"/>
            </testcase>
            """,
        )

        val failure = TestReports.parse(repo).single()

        assertEquals(
            Diagnostic(
                null,
                null,
                null,
                "java.util.concurrent.TimeoutException",
                "timed out",
                Severity.ERROR,
                "surefire",
                "com.example.FooTest#bar",
            ),
            failure,
        )
    }

    @Test
    fun `a failure without message, type, or stack trace still is a failure`() {
        report("""<testcase name="bar" classname="com.example.FooTest"><failure/></testcase>""")

        assertEquals(
            Diagnostic(null, null, null, null, "Test failed", Severity.ERROR, "surefire", "com.example.FooTest#bar"),
            TestReports.parse(repo).single(),
        )
    }

    @Test
    fun `a frame whose source file can't be found has no location`() {
        report(
            """
            <testcase name="bar" classname="FooTest">
              <failure message="boom" type="java.lang.AssertionError">java.lang.AssertionError: boom
            	at FooTest.bar(FooTest.java:3)
            </failure>
            </testcase>
            """,
        )

        val failure = TestReports.parse(repo).single()

        assertEquals(null, failure.file)
        assertEquals(null, failure.line)
        assertEquals("FooTest#bar", failure.test)
    }

    @Test
    fun `a test in the default package is found in the source root`() {
        source("src/test/java/FooTest.java")
        report(
            """
            <testcase name="bar" classname="FooTest">
              <failure message="boom" type="java.lang.AssertionError">java.lang.AssertionError: boom
            	at FooTest.bar(FooTest.java:3)
            </failure>
            </testcase>
            """,
        )

        assertEquals("src/test/java/FooTest.java", TestReports.parse(repo).single().file)
    }

    @Test
    fun `passed, skipped, and flaky tests are no failures`() {
        report(
            """
            <testcase name="passes" classname="com.example.FooTest"/>
            <testcase name="skipped" classname="com.example.FooTest"><skipped/></testcase>
            <testcase name="flaky" classname="com.example.FooTest">
              <flakyFailure message="once" type="java.lang.AssertionError"/>
            </testcase>
            """,
        )

        assertEquals(emptyList(), TestReports.parse(repo))
    }

    @Test
    fun `reports outside the build directory are ignored`() {
        repo.resolve("docs/surefire-reports/TEST-com.example.FooTest.xml").createParentDirectories().writeText("<testsuite/>")
        repo.resolve("target/surefire-reports/com.example.FooTest.txt").createParentDirectories().writeText("text report")

        assertEquals(emptyList(), TestReports.parse(repo))
    }

    @Test
    fun `an unreadable report is a build error, so a failure can't get lost`() {
        repo
            .resolve(
                "target/surefire-reports",
            ).createDirectories()
            .resolve("TEST-com.example.FooTest.xml")
            .writeText("<testsuite><testcase")

        val problem = TestReports.parse(repo).single()

        assertEquals(null, problem.test)
        assertEquals("maven", problem.tool)
        assertContains(problem.message, "Can't read test report target/surefire-reports/TEST-com.example.FooTest.xml")
    }

    @Test
    fun `a report that can't be opened is a build error too`() {
        val report = repo.resolve("target/surefire-reports").createDirectories().resolve("TEST-com.example.FooTest.xml")
        report.writeText("<testsuite/>")
        report.toFile().setReadable(false)

        val problem = TestReports.parse(repo).single()

        assertEquals(null, problem.test)
        assertContains(problem.message, "Can't read test report target/surefire-reports/TEST-com.example.FooTest.xml")
    }

    @Test
    fun `a report with a DOCTYPE is not read`() {
        repo.resolve("target/surefire-reports").createDirectories().resolve("TEST-com.example.FooTest.xml").writeText(
            """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><testsuite>&e;</testsuite>""",
        )

        assertContains(TestReports.parse(repo).single().message, "Can't read test report")
    }

    private fun source(path: String) = repo.resolve(path).createParentDirectories().writeText("// test source")

    @Test
    fun `the tests run are the test cases of every report, passed, failed, and skipped`() {
        val fixture = requireNotNull(javaClass.getResource("/fixtures/failing-tests")).toURI().toPath()
        report("<testcase name=\"passes\"/><testcase name=\"skipped\"><skipped/></testcase>")

        assertEquals(4, TestReports.count(fixture))
        assertEquals(2, TestReports.count(repo))
    }

    @Test
    fun `without readable reports the tests run are unknown`() {
        assertEquals(null, TestReports.count(repo), "no reports: no test ran")

        report("<testcase")

        assertEquals(null, TestReports.count(repo))
    }

    private fun report(testcases: String) =
        repo
            .resolve("target/surefire-reports/TEST-com.example.FooTest.xml")
            .createParentDirectories()
            .writeText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite>${testcases.trimIndent()}</testsuite>")
}
