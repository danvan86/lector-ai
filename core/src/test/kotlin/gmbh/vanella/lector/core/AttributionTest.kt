package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AttributionTest {
    private val head = Path.of("/work/head")
    private val baseCheckout = Path.of("/work/base")

    private val compileError = Diagnostic("src/App.java", 5, 9, null, "cannot find symbol", Severity.ERROR, "javac")
    private val buildError = Diagnostic(null, null, null, null, "Could not resolve dependencies", Severity.ERROR, "maven")
    private val brokenTest = testFailure("com.example.BrokenTest#fails")
    private val oldBug = testFailure("com.example.OldBugTest#fails")

    @Test
    fun `every build and test run gets the time that is left when it starts, on the base too`() {
        val adapter = FakeAdapter(headVerify = listOf(brokenTest), headRerun = listOf(brokenTest), baseRun = emptyList())
        val left = ArrayDeque(listOf(30.minutes, 20.minutes, 10.minutes))

        attribute(adapter, head, FakeBase(baseCheckout)) { left.removeFirst() }

        assertEquals(listOf(30.minutes, 20.minutes, 10.minutes), adapter.timeouts)
    }

    @Test
    fun `without a time limit every run may take as long as it takes`() {
        val adapter = FakeAdapter(headVerify = listOf(brokenTest), headRerun = listOf(brokenTest), baseRun = emptyList())

        attribute(adapter, head, FakeBase(baseCheckout))

        assertEquals(listOf(Duration.INFINITE, Duration.INFINITE, Duration.INFINITE), adapter.timeouts)
    }

    @Test
    fun `a clean build has nothing to report and runs no tests again`() {
        val adapter = FakeAdapter(headVerify = emptyList())

        val verification = attribute(adapter, head, base = null)

        assertEquals(Verification(), verification)
        assertTrue(adapter.runTestsCalls.isEmpty())
    }

    @Test
    fun `a compile error is caused by the update without any rerun`() {
        val adapter = FakeAdapter(headVerify = listOf(compileError))

        val verification = attribute(adapter, head, base = null)

        assertEquals(Verification(causedByUpdate = listOf(compileError)), verification)
        assertTrue(adapter.runTestsCalls.isEmpty())
    }

    @Test
    fun `a build error without a location is caused by the update`() {
        val adapter = FakeAdapter(headVerify = listOf(buildError))

        assertEquals(Verification(causedByUpdate = listOf(buildError)), attribute(adapter, head, base = null))
    }

    @Test
    fun `a test that passes on the rerun is flaky and the base is not checked out`() {
        val adapter = FakeAdapter(headVerify = listOf(brokenTest), headRerun = emptyList())
        val base = FakeBase(baseCheckout)

        val verification = attribute(adapter, head, base)

        assertEquals(Verification(flaky = listOf(brokenTest)), verification)
        assertEquals(listOf(head to listOf(brokenTest)), adapter.runTestsCalls)
        assertEquals(0, base.checkouts)
    }

    @Test
    fun `a test that fails on head and on base is pre-existing`() {
        val adapter = FakeAdapter(headVerify = listOf(oldBug), headRerun = listOf(oldBug), baseRun = listOf(oldBug))

        val verification = attribute(adapter, head, FakeBase(baseCheckout))

        assertEquals(Verification(preExisting = listOf(oldBug)), verification)
    }

    @Test
    fun `a test that fails on head and passes on base is caused by the update`() {
        val adapter = FakeAdapter(headVerify = listOf(brokenTest), headRerun = listOf(brokenTest), baseRun = emptyList())

        val verification = attribute(adapter, head, FakeBase(baseCheckout))

        assertEquals(Verification(causedByUpdate = listOf(brokenTest)), verification)
    }

    @Test
    fun `without a base commit a still-failing test is unattributed`() {
        val adapter = FakeAdapter(headVerify = listOf(brokenTest), headRerun = listOf(brokenTest))

        val verification = attribute(adapter, head, base = null)

        assertEquals(Verification(unattributed = listOf(brokenTest)), verification)
        assertEquals(listOf(head to listOf(brokenTest)), adapter.runTestsCalls)
    }

    @Test
    fun `the base runs only the still-failing tests, once, in its own checkout`() {
        val flakyTest = testFailure("com.example.FlakyTest#sometimes")
        val adapter =
            FakeAdapter(
                headVerify = listOf(brokenTest, flakyTest, oldBug),
                headRerun = listOf(brokenTest, oldBug),
                baseRun = listOf(oldBug),
            )
        val base = FakeBase(baseCheckout)

        val verification = attribute(adapter, head, base)

        assertEquals(
            listOf(
                head to listOf(brokenTest, flakyTest, oldBug),
                baseCheckout to listOf(brokenTest, oldBug),
            ),
            adapter.runTestsCalls,
        )
        assertEquals(1, base.checkouts)
        assertEquals(
            Verification(causedByUpdate = listOf(brokenTest), preExisting = listOf(oldBug), flaky = listOf(flakyTest)),
            verification,
        )
    }

    @Test
    fun `build errors next to test failures are caused by the update and listed first`() {
        val adapter = FakeAdapter(headVerify = listOf(oldBug, buildError), headRerun = listOf(oldBug), baseRun = emptyList())

        val verification = attribute(adapter, head, FakeBase(baseCheckout))

        assertEquals(Verification(causedByUpdate = listOf(buildError, oldBug)), verification)
    }

    @Test
    fun `a rerun that breaks the build counts every test as still failing`() {
        val adapter = FakeAdapter(headVerify = listOf(brokenTest), headRerun = listOf(buildError))

        val verification = attribute(adapter, head, base = null)

        assertEquals(Verification(unattributed = listOf(brokenTest)), verification)
    }

    @Test
    fun `a base that doesn't build leaves the still-failing tests unattributed`() {
        val adapter = FakeAdapter(headVerify = listOf(brokenTest), headRerun = listOf(brokenTest), baseRun = listOf(compileError))

        val verification = attribute(adapter, head, FakeBase(baseCheckout))

        assertEquals(Verification(unattributed = listOf(brokenTest)), verification)
    }

    @Test
    fun `tests are matched by name, not by message`() {
        val rerun = brokenTest.copy(message = "a different message on the rerun")
        val adapter = FakeAdapter(headVerify = listOf(brokenTest), headRerun = listOf(rerun))

        val verification = attribute(adapter, head, base = null)

        assertEquals(Verification(unattributed = listOf(brokenTest)), verification)
    }

    @Test
    fun `the number of tests run is read after verify, before the reruns`() {
        val adapter =
            FakeAdapter(headVerify = listOf(brokenTest), headRerun = listOf(brokenTest), testsRun = { calls ->
                if (calls ==
                    0
                ) {
                    12
                } else {
                    1
                }
            })

        assertEquals(12, attribute(adapter, head, base = null).testsRun)
        val withBase =
            FakeAdapter(listOf(brokenTest), listOf(brokenTest), listOf(brokenTest), testsRun = { calls ->
                if (calls ==
                    0
                ) {
                    12
                } else {
                    1
                }
            })
        assertEquals(12, attribute(withBase, head, FakeBase(baseCheckout)).testsRun)
    }

    private fun testFailure(test: String) =
        Diagnostic("src/test/Test.java", 12, null, "AssertionError", "expected <1> but was <2>", Severity.ERROR, "surefire", test)

    /** Returns canned results. `runTests` answers with [headRerun] in the head and [baseRun] in the base checkout. */
    private inner class FakeAdapter(
        private val headVerify: List<Diagnostic>,
        private val headRerun: List<Diagnostic> = emptyList(),
        private val baseRun: List<Diagnostic> = emptyList(),
        private val testsRun: (calls: Int) -> Int? = { null },
    ) : EcosystemAdapter {
        val runTestsCalls = mutableListOf<Pair<Path, List<Diagnostic>>>()
        val timeouts = mutableListOf<Duration>()

        override val ecosystem = "test"

        override val languageSupport = LanguageSupport { _, _ -> null }

        override fun isEditable(file: String) = true

        override fun testsRun(repo: Path) = testsRun(runTestsCalls.size)

        override fun testDisablingConfig(
            file: String,
            content: String,
            line: Int,
        ): String? = null

        override val versionScheme = DottedVersions
        override val recipeRunner = null
        override val resolvedVersions = null

        override fun detect(repo: Path) = true

        override fun verify(
            repo: Path,
            timeout: Duration,
        ) = headVerify.also { timeouts += timeout }

        override fun runTests(
            repo: Path,
            tests: List<Diagnostic>,
            timeout: Duration,
        ): List<Diagnostic> {
            runTestsCalls += repo to tests
            timeouts += timeout
            return if (repo == head) headRerun else baseRun
        }

        override fun ground(
            repo: Path,
            change: DependencyChange,
            diagnostics: List<Diagnostic>,
        ) = ""

        override fun clusterKey(diagnostic: Diagnostic) = null

        override fun isBuildFile(file: String) = false
    }

    private class FakeBase(
        private val checkout: Path,
    ) : BaseCommit {
        var checkouts = 0

        override fun <T> checkout(block: (Path) -> T): T {
            checkouts++
            return block(checkout)
        }
    }
}
