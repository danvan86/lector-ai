package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Attempts that edit a build file: the lockfiles follow them, and nothing may resolve lower afterwards. */
class FixLoopBuildFileTest : FixLoopFixture() {
    @Test
    fun `after a build-file edit the lockfile follows it before the downgrade check, and goes into the edit's commit`() {
        lockfile()
        var updates = 0
        val adapter =
            FakeAdapter(versions = ::lockedVersions, lockfiles = { dir ->
                updates++
                dir.resolve("pom.lock").writeText(dir.resolve("pom.xml").readText())
                LockfileUpdate.Updated(listOf("pom.lock"))
            })
        val raise = Edit("pom.xml", "org.example:other 3.0", "org.example:other 3.1")

        val result = loop(adapter, ScriptedLlm(edits(fix, raise))).run(repo, context, adapter.initial())

        assertIs<AttemptOutcome.Accepted>(result.attempts.single().outcome)
        assertEquals(1, updates)
        assertEquals("App.java\npom.lock\npom.xml", git("show", "--format=", "--name-only", "HEAD").trim())
        assertEquals("", git("status", "--porcelain"))
    }

    @Test
    fun `a lockfile that moves a package lower after a build-file edit rejects the attempt`() {
        lockfile()
        val adapter =
            FakeAdapter(versions = ::lockedVersions, lockfiles = { dir ->
                dir.resolve("pom.lock").writeText("$lib 2.0\norg.example:other 2.0\n")
                LockfileUpdate.Updated(listOf("pom.lock"))
            })
        val raise = Edit("pom.xml", "org.example:other 3.0", "org.example:other 3.1")

        val result = loop(adapter, ScriptedLlm(edits(fix, raise)), Budgets(attempts = 1)).run(repo, context, adapter.initial())

        assertEquals(
            AttemptOutcome.Rejected("the edits move packages to lower versions: org.example:other 3.0 -> 2.0"),
            result.attempts.single().outcome,
        )
        assertEquals("", git("status", "--porcelain"))
    }

    @Test
    fun `a lockfile that can't follow a build-file edit rejects the attempt and leaves no change behind`() {
        lockfile()
        val adapter =
            FakeAdapter(versions = ::lockedVersions, lockfiles = { dir ->
                dir.resolve("pom.lock").writeText("half written\n")
                LockfileUpdate.Failed("the install failed: no version 3.1")
            })
        val raise = Edit("pom.xml", "org.example:other 3.0", "org.example:other 3.1")

        val result = loop(adapter, ScriptedLlm(edits(fix, raise)), Budgets(attempts = 1)).run(repo, context, adapter.initial())

        assertEquals(
            AttemptOutcome.Rejected("the lockfile can't follow the edits: the install failed: no version 3.1"),
            result.attempts.single().outcome,
        )
        assertEquals(0, adapter.verifyCalls)
        assertEquals("", git("status", "--porcelain"))
    }

    @Test
    fun `a lockfile a rejected attempt created stays out of the next attempt's commit`() {
        val adapter =
            FakeAdapter(versions = { mapOf(lib to "2.0") }, lockfiles = { dir ->
                dir.resolve("new.lock").writeText("created\n")
                LockfileUpdate.Updated(listOf("new.lock"))
            })
        val raise = Edit("pom.xml", "org.example:other 3.0", "org.example:other 3.1")

        val result = loop(adapter, ScriptedLlm(edits(raise), edits(fix))).run(repo, context, adapter.initial())

        assertIs<AttemptOutcome.Rejected>(result.attempts.first().outcome, "no progress")
        assertIs<AttemptOutcome.Accepted>(result.attempts.last().outcome)
        assertEquals("App.java", git("show", "--format=", "--name-only", "HEAD").trim())
    }

    @Test
    fun `an attempt that edits no build file leaves the lockfile alone`() {
        var updates = 0
        val adapter =
            FakeAdapter(lockfiles = {
                updates++
                LockfileUpdate.Updated(emptyList())
            })

        loop(adapter, ScriptedLlm(edits(fix))).run(repo, context, adapter.initial())

        assertEquals(0, updates)
    }

    @Test
    fun `an attempt that makes the PR's own package resolve lower is rejected before it is verified`() {
        val adapter = FakeAdapter()
        val revert = Edit("pom.xml", "$lib 2.0", "$lib 1.0")

        val raise = Edit("pom.xml", "org.example:other 3.0", "org.example:other 3.1")

        val result = loop(adapter, ScriptedLlm(edits(fix, revert), edits(fix, raise))).run(repo, context, adapter.initial())

        assertEquals(
            AttemptOutcome.Rejected("the edits move packages to lower versions: $lib 2.0 -> 1.0"),
            result.attempts.first().outcome,
        )
        assertIs<AttemptOutcome.Accepted>(result.attempts.last().outcome, "compared with the versions before the loop")
        assertEquals(1, adapter.verifyCalls)
    }

    @Test
    fun `versions that can't be read after the edits reject the attempt`() {
        val adapter =
            FakeAdapter(versions = { dir ->
                if ("unreadable" in dir.resolve("pom.xml").readText()) throw ToolError("the POM is broken")
                mapOf(lib to "2.0")
            })
        val breakPom = Edit("pom.xml", "org.example:other 3.0", "unreadable")

        val result = loop(adapter, ScriptedLlm(edits(fix, breakPom)), Budgets(attempts = 1)).run(repo, context, adapter.initial())

        assertEquals(
            AttemptOutcome.Rejected("the resolved versions can't be read, so a downgrade can't be ruled out: the POM is broken"),
            result.attempts.single().outcome,
        )
    }

    @Test
    fun `an attempt that makes any other package resolve lower is rejected too`() {
        val adapter = FakeAdapter()
        val pin = Edit("pom.xml", "org.example:other 3.0", "org.example:other 2.9")

        val result = loop(adapter, ScriptedLlm(edits(fix, pin), edits(fix))).run(repo, context, adapter.initial())

        assertEquals(
            AttemptOutcome.Rejected("the edits move packages to lower versions: org.example:other 3.0 -> 2.9"),
            result.attempts.first().outcome,
        )
    }

    @Test
    fun `a build-file edit that moves nothing lower is verified like any other`() {
        val adapter = FakeAdapter()
        val raise = Edit("pom.xml", "org.example:other 3.0", "org.example:other 3.1")

        val result = loop(adapter, ScriptedLlm(edits(fix, raise))).run(repo, context, adapter.initial())

        assertIs<AttemptOutcome.Accepted>(result.attempts.single().outcome)
        assertEquals("org.example:other 3.1", repo.resolve("pom.xml").readLines().last())
    }

    @Test
    fun `an attempt out of scope is rejected for its scope before the versions are read`() {
        val raise = Edit("pom.xml", "org.example:other 3.0", "org.example:other 3.1")
        val adapter = FakeAdapter(versions = { throw ToolError("resolution failed") })
        repo.resolve("untracked.txt").writeText("a")

        val result =
            loop(adapter, ScriptedLlm(edits(raise, Edit("untracked.txt", "a", "b"))), Budgets(attempts = 1))
                .run(repo, context, adapter.initial())

        assertEquals(listOf("scope"), (result.attempts.single().outcome as AttemptOutcome.Rejected).violations.map { it.rule })
    }

    @Test
    fun `without readable versions a build-file edit is rejected`() {
        val raise = Edit("pom.xml", "org.example:other 3.0", "org.example:other 3.1")
        val cantRead = FakeAdapter(versions = null)
        val failsToRead = FakeAdapter(versions = { throw ToolError("resolution failed") })

        val noCapability = loop(cantRead, ScriptedLlm(edits(fix, raise)), Budgets(attempts = 1)).run(repo, context, cantRead.initial())
        val failure = loop(failsToRead, ScriptedLlm(edits(fix, raise)), Budgets(attempts = 1)).run(repo, context, failsToRead.initial())

        assertEquals(
            AttemptOutcome.Rejected(
                "the resolved versions can't be read, so a downgrade can't be ruled out: test can't read resolved versions",
            ),
            noCapability.attempts.first().outcome,
        )
        assertEquals(
            AttemptOutcome.Rejected("the resolved versions can't be read, so a downgrade can't be ruled out: resolution failed"),
            failure.attempts.first().outcome,
        )
    }

    /** A tracked `pom.lock`, the lockfile of `pom.xml` in these tests. */
    private fun lockfile() {
        repo.resolve("pom.lock").writeText(repo.resolve("pom.xml").readText())
        git("add", "pom.lock")
        git("commit", "--quiet", "-m", "lockfile")
    }

    private fun lockedVersions(dir: Path) =
        dir
            .resolve("pom.lock")
            .readLines()
            .filter { it.isNotBlank() }
            .associate { it.substringBefore(' ') to it.substringAfter(' ') }
}
