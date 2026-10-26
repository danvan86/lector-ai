package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * M11 section 2: a build error without a location that the base commit has too is pre-existing, if the head ran its
 * tests. The base is verified only when needed, at most once per run, and a base that can't be verified never ends the run.
 * A run's base commit is a [RunBase]: it remembers what the base's verification found, so every attribution of the run
 * shares it.
 */
class BuildErrorAttributionTest : BuildErrorFixture() {
    @TempDir
    lateinit var elsewhere: Path

    @Test
    fun `a build error the base commit has too is pre-existing when the head ran its tests, and the model never sees it`() {
        val adapter = TwoCheckoutAdapter(head = { dir -> compileErrors(dir) + gpg }, base = { listOf(gpg) })
        val base = CountingBase(baseCheckout)
        val llm = ScriptedLlm(edits(fix))

        val initial = attribute(adapter, repo, base)

        assertEquals(listOf(gpg), initial.preExisting)
        assertEquals(listOf(compileError(3)), initial.causedByUpdate)

        val result = loop(adapter, llm, base = base).run(repo, context, initial)

        assertEquals(StopReason.NOTHING_LEFT, result.stopReason, "the compile error was all the update caused")
        assertEquals(listOf(gpg), result.verification.preExisting, "still reported after the fix")
        assertTrue(llm.requests.none { gpg in it.diagnostics }, "no request carries the pre-existing build error")
    }

    @Test
    fun `a build error the base commit has too still counts when the head ran no tests, or can't tell`() {
        listOf(0, null).forEach { testsRun ->
            val adapter = TwoCheckoutAdapter(head = { listOf(gpg) }, base = { listOf(gpg) }, testsRun = testsRun)

            val verification = attribute(adapter, repo, CountingBase(baseCheckout))

            assertEquals(listOf(gpg), verification.causedByUpdate, "tests run: $testsRun")
            assertEquals(emptyList(), verification.preExisting, "tests run: $testsRun")
        }
    }

    @Test
    fun `a build error only the head has is caused by the update, also when the base fails differently`() {
        val surefire =
            buildError(
                "Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.5:test (default-test) on project app: " +
                    "There are test failures.",
            )
        listOf(emptyList(), listOf(surefire)).forEach { onBase ->
            val adapter = TwoCheckoutAdapter(head = { listOf(gpg) }, base = { onBase })

            val verification = attribute(adapter, repo, CountingBase(baseCheckout))

            assertEquals(listOf(gpg), verification.causedByUpdate, "base: $onBase")
            assertEquals(emptyList(), verification.preExisting, "base: $onBase")
        }
    }

    @Test
    fun `a base whose build stops earlier, at compile errors, leaves the head's later build error caused by the update`() {
        val adapter = TwoCheckoutAdapter(head = { listOf(gpg) }, base = { listOf(compileError(3)) })

        val verification = attribute(adapter, repo, CountingBase(baseCheckout))

        assertEquals(listOf(gpg), verification.causedByUpdate)
        assertEquals(emptyList(), verification.preExisting)
    }

    @Test
    fun `the base commit is verified once per run, however many attributions see a build error`() {
        val adapter = TwoCheckoutAdapter(head = { dir -> compileErrors(dir) + gpg }, base = { listOf(gpg) })
        val base = RunBase(CountingBase(baseCheckout))
        val noFix = Edit("App.java", "broken();", "broken(); // still broken")
        val llm =
            ScriptedLlm(
                edits(noFix),
                edits(noFix.copy(replace = "broken(); // again")),
                edits(noFix.copy(replace = "broken(); // once more")),
            )

        val initial = attribute(adapter, repo, base)
        val result = loop(adapter, llm, Budgets(attemptsWithoutProgress = 3), base).run(repo, context, initial)

        assertEquals(3, result.attempts.size)
        assertEquals(1, adapter.baseVerifications, "one base verification for the first attribution and three attempts")
    }

    @Test
    fun `the base commit is not verified when no build error needs it`() {
        val adapter = TwoCheckoutAdapter(head = ::compileErrors)
        val checkouts = CountingBase(baseCheckout)
        val base = RunBase(checkouts)

        val initial = attribute(adapter, repo, base)
        loop(adapter, ScriptedLlm(edits(fix)), base = base).run(repo, context, initial)

        assertEquals(0, adapter.baseVerifications)
        assertEquals(0, checkouts.checkouts)
    }

    @Test
    fun `two build errors that differ only in the checkout's path match, also when the base names its real path`() {
        val real = Files.createDirectory(elsewhere.resolve("base-real"))
        val link = Files.createSymbolicLink(elsewhere.resolve("base-link"), real)
        val adapter = TwoCheckoutAdapter(head = { dir -> listOf(gpgIn(dir)) }, base = { dir -> listOf(gpgIn(dir.toRealPath())) })

        val verification = attribute(adapter, repo, CountingBase(link))

        assertEquals(listOf(gpgIn(repo)), verification.preExisting)
        assertEquals(emptyList(), verification.causedByUpdate)

        val otherPlugin =
            TwoCheckoutAdapter(
                head = { dir -> listOf(gpgIn(dir)) },
                base = { dir -> listOf(buildError(gpgIn(dir).message.replace("3.2.7", "3.2.8"))) },
            )

        assertEquals(listOf(gpgIn(repo)), attribute(otherPlugin, repo, CountingBase(link)).causedByUpdate, "more than the path differs")
    }

    @Test
    fun `without a base commit a build error is caused by the update`() {
        val adapter = TwoCheckoutAdapter(head = { listOf(gpg) })

        assertEquals(Verification(causedByUpdate = listOf(gpg), testsRun = 12), attribute(adapter, repo, base = null))
    }

    @Test
    fun `a base that fails to verify leaves the build error caused by the update, and is not tried again in the run`() {
        listOf(ToolError("mvn failed to start"), ProcessTimedOut("mvn did not finish within 1h")).forEach { failure ->
            val adapter = TwoCheckoutAdapter(head = { listOf(gpg) }, base = { throw failure })
            val base = RunBase(CountingBase(baseCheckout))

            val first = attribute(adapter, repo, base)
            val second = attribute(adapter, repo, base)

            assertEquals(listOf(gpg), first.causedByUpdate, failure.message)
            assertEquals(listOf(gpg), second.causedByUpdate, failure.message)
            assertEquals(1, adapter.baseVerifications, "${failure.message}: not tried again")
        }
    }

    @Test
    fun `a base that can't be checked out leaves the build error caused by the update, and is not tried again in the run`() {
        val adapter = TwoCheckoutAdapter(head = { listOf(gpg) }, base = { listOf(gpg) })
        val checkouts = CountingBase(baseCheckout, failure = ToolError("git worktree add failed"))
        val base = RunBase(checkouts)

        val first = attribute(adapter, repo, base)
        val second = attribute(adapter, repo, base)

        assertEquals(listOf(gpg), first.causedByUpdate)
        assertEquals(listOf(gpg), second.causedByUpdate)
        assertEquals(1, checkouts.checkouts, "not tried again")
    }

    @Test
    fun `a base that times out inside an attempt doesn't end the run`() {
        val adapter =
            TwoCheckoutAdapter(
                head = { dir -> compileErrors(dir) + if ("fixed();" in dir.resolve("App.java").readText()) listOf(gpg) else emptyList() },
                base = { throw ProcessTimedOut("mvn did not finish within 1h") },
            )
        val base = RunBase(CountingBase(baseCheckout))

        val result = loop(adapter, ScriptedLlm(edits(fix)), Budgets(attempts = 1), base).run(repo, context, attribute(adapter, repo, base))

        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts.single().outcome, "the attempt ended with a result")
        assertContains(rejected.reason, "maven-gpg-plugin", message = "without the base, the build error counts against the attempt")
        assertEquals(1, adapter.baseVerifications)
    }
}
