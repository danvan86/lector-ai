package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class FixLoopTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var baseCheckout: Path

    private val lib = "org.example:lib"
    private val change = DependencyChange("test", lib, "1.0", "2.0", UpdateType.MAJOR)
    private val context =
        FixContext(change, releaseNotes = "2.0 renames broken() to fixed()", recipeDiff = "diff --git a/pom.xml b/pom.xml")
    private val time = TestTimeSource()
    private val fix = Edit("App.java", "broken();", "fixed();")
    private val app =
        """
        class App {
            void run() {
                broken();
            }
        }
        """.trimIndent() + "\n"

    @BeforeTest
    fun `create a repository whose code the update broke`() {
        git("init", "--quiet")
        repo.resolve("App.java").writeText(app)
        repo.resolve("pom.xml").writeText("$lib 2.0\norg.example:other 3.0\n")
        git("add", ".")
        git("commit", "--quiet", "-m", "head")
    }

    @Test
    fun `an attempt that makes progress becomes one commit by lector, and the loop stops once nothing is left`() {
        val llm = ScriptedLlm(edits(fix, rationale = "Call fixed() instead of broken()"))
        val adapter = FakeAdapter()

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        assertEquals(StopReason.NOTHING_LEFT, result.stopReason)
        assertEquals(Verification(), result.verification)
        val attempt = result.attempts.single()
        assertEquals("symbol:broken", attempt.cluster)
        assertEquals(listOf(fix), attempt.edits)
        val accepted = assertIs<AttemptOutcome.Accepted>(attempt.outcome)
        assertEquals(git("rev-parse", "HEAD").trim(), accepted.commit)
        assertEquals("Call fixed() instead of broken()", accepted.rationale)
        assertEquals(Progress(0, 1, 0), accepted.before)
        assertEquals(Progress(0, 0, 0), accepted.after)
        assertEquals("lector <lector@localhost>", git("log", "-1", "--format=%an <%ae>").trim())
        assertEquals("Call fixed() instead of broken()", git("log", "-1", "--format=%s").trim())
        assertEquals("Addresses:\n- [javac] App.java:3: cannot find symbol broken", git("log", "-1", "--format=%b").trim())
        assertEquals(app.replace("broken();", "fixed();"), repo.resolve("App.java").readText())
        assertEquals("", git("status", "--porcelain"))
        assertEquals(1, llm.requests.size, "no further request once nothing is left")
    }

    @Test
    fun `an accepted fix commits only the files its edits changed, not what the build left behind`() {
        val adapter =
            FakeAdapter(problems = { dir ->
                dir.resolve("test-output.log").writeText("written by the build\n")
                compileErrors(dir)
            })

        loop(adapter, ScriptedLlm(edits(fix))).run(repo, context, adapter.initial())

        assertEquals("App.java", git("show", "--name-only", "--format=", "HEAD").trim())
        assertEquals("?? test-output.log", git("status", "--porcelain").trim())
    }

    @Test
    fun `an edit to a file Git ignores breaks the scope, and is never written`() {
        repo.resolve(".gitignore").writeText("generated/\n")
        git("add", ".gitignore")
        git("commit", "--quiet", "-m", "ignore generated code")
        repo.resolve("generated").toFile().mkdir()
        repo.resolve("generated/Gen.java").writeText("broken();\n")
        val adapter = FakeAdapter()

        val result =
            loop(
                adapter,
                ScriptedLlm(edits(Edit("./App.java", "broken();", "fixed();"), Edit("generated/Gen.java", "broken", "fixed"))),
                Budgets(attempts = 1),
            ).run(repo, context, adapter.initial())

        val violation = Violation("scope", "generated/Gen.java", null, "Git doesn't track the file, so it is generated or ignored")
        assertEquals(
            AttemptOutcome.Rejected("the edits break the guardrails:\n- $violation", listOf(violation)),
            result.attempts.first().outcome,
        )
        assertEquals("broken();\n", repo.resolve("generated/Gen.java").readText())
        assertEquals(app, repo.resolve("App.java").readText())
        assertEquals(0, adapter.verifyCalls)
    }

    @Test
    fun `an attempt that breaks a guardrail is reset, counted, never verified, and the retry learns what it broke`() {
        val stub = Edit("App.java", "broken();", "return null;")
        val llm = ScriptedLlm(edits(stub), edits(stub))
        val adapter = FakeAdapter()

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        val violation = Violation("stubbing", "App.java", 3, "the edits add the placeholder `return null;` in place of logic")
        val rejected =
            AttemptOutcome.Rejected(
                "the edits break the guardrails:\n- [stubbing] App.java:3: ${violation.reason}",
                listOf(violation),
            )
        assertEquals(listOf(rejected, rejected), result.attempts.map { it.outcome })
        assertEquals(StopReason.NO_PROGRESS, result.stopReason, "rejections count against the budgets")
        assertEquals(
            PreviousAttempt(listOf(stub), rejected.reason),
            llm.requests
                .last()
                .previousAttempts
                .first(),
        )
        assertEquals(0, adapter.verifyCalls, "a violating attempt is never verified")
        assertEquals("", git("status", "--porcelain"))
        assertEquals(app, repo.resolve("App.java").readText())
    }

    @Test
    fun `the guardrails see the diagnostics caused by the update before the attempt`() {
        val delete = Edit("App.java", "    void run() {\n        broken();\n    }\n", "")
        val overrides = Diagnostic("App.java", 2, 5, "override", "method run() does not override a method", Severity.ERROR, "javac")
        val adapter = FakeAdapter()

        val named = loop(adapter, ScriptedLlm(edits(delete))).run(repo, context, Verification(listOf(overrides)))
        git("reset", "--hard", "--quiet", "HEAD~1")
        val unnamed = loop(adapter, ScriptedLlm(edits(delete)), Budgets(attempts = 1)).run(repo, context, adapter.initial())

        assertIs<AttemptOutcome.Accepted>(named.attempts.single().outcome)
        assertEquals(listOf("symbol-comparison"), (unnamed.attempts.single().outcome as AttemptOutcome.Rejected).violations.map { it.rule })
    }

    @Test
    fun `the diff-size limit is configurable`() {
        val adapter = FakeAdapter()

        val result =
            loop(
                adapter,
                ScriptedLlm(edits(fix)),
                Budgets(attempts = 1, changedLinesPerAttempt = 1),
            ).run(repo, context, adapter.initial())

        assertEquals(listOf("diff-size"), (result.attempts.single().outcome as AttemptOutcome.Rejected).violations.map { it.rule })
    }

    @Test
    fun `a tool error inside an attempt resets the working tree before it ends the loop`() {
        val hang = Edit("App.java", "broken();", "while (true) {}")
        val adapter =
            FakeAdapter(problems = { dir ->
                if ("while (true)" in dir.resolve("App.java").readText()) throw ToolError("mvn did not finish within 1h")
                compileErrors(dir)
            })
        val initial = adapter.initial()

        val error = assertFailsWith<ToolError> { loop(adapter, ScriptedLlm(edits(hang))).run(repo, context, initial) }

        assertEquals("mvn did not finish within 1h", error.message)
        assertEquals("", git("status", "--porcelain"))
        assertEquals(app, repo.resolve("App.java").readText())
    }

    @Test
    fun `any other failure inside an attempt resets the working tree too`() {
        val adapter = FakeAdapter(languageSupport = { _, _ -> throw UnsatisfiedLinkError("no tree-sitter for this platform") })
        val initial = adapter.initial()

        val error = assertFailsWith<UnsatisfiedLinkError> { loop(adapter, ScriptedLlm(edits(fix))).run(repo, context, initial) }

        assertEquals("no tree-sitter for this platform", error.message)
        assertEquals("", git("status", "--porcelain"))
        assertEquals(app, repo.resolve("App.java").readText())
    }

    @Test
    fun `the first request on a cluster has temperature 0 and everything the model needs`() {
        val llm = ScriptedLlm(edits(fix))
        val adapter = FakeAdapter()

        loop(adapter, llm).run(repo, context, adapter.initial())

        val request = llm.requests.single()
        assertEquals(change, request.change)
        assertEquals(listOf(compileError(3)), request.diagnostics)
        assertEquals(listOf(Snippet("App.java", 1, 5, app)), request.snippets)
        assertEquals("API of $lib 2.0 for 1 diagnostics", request.grounding)
        assertEquals("2.0 renames broken() to fixed()", request.releaseNotes)
        assertEquals("diff --git a/pom.xml b/pom.xml", request.recipeDiff)
        assertEquals(emptyList(), request.previousAttempts)
        assertEquals(0.0, request.temperature)
    }

    @Test
    fun `a build error without a location gets the build files as snippets`() {
        val buildError = Diagnostic(null, null, null, "artifact:lib", "Could not find artifact $lib:jar:2.0", Severity.ERROR, "maven")
        val llm = ScriptedLlm(edits(Edit("App.java", "nothing", "else")))
        val adapter = FakeAdapter(problems = { listOf(buildError) })

        loop(adapter, llm, Budgets(attempts = 1)).run(repo, context, adapter.initial())

        assertEquals(
            listOf("pom.xml"),
            llm.requests
                .single()
                .snippets
                .map { it.file },
        )
    }

    @Test
    fun `only diagnostics caused by the update reach the model`() {
        val preExisting = Diagnostic("Old.java", 1, null, null, "expected 1", Severity.ERROR, "surefire", "OldTest#old")
        val flaky = Diagnostic("Flaky.java", 1, null, null, "timeout", Severity.ERROR, "surefire", "FlakyTest#flaky")
        val llm = ScriptedLlm(edits(fix))

        loop(FakeAdapter(), llm).run(repo, context, Verification(listOf(compileError(3)), listOf(preExisting), listOf(flaky)))

        assertEquals(listOf(compileError(3)), llm.requests.single().diagnostics)
    }

    @Test
    fun `an edit that doesn't apply rejects the attempt, and the retry gets the edits, the reason, and a higher temperature`() {
        val wrong = Edit("App.java", "broken(1);", "fixed(1);")
        val llm = ScriptedLlm(edits(wrong), edits(fix))
        val adapter = FakeAdapter()

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        val reason = "App.java: the search text is not in the file"
        assertEquals(AttemptOutcome.Rejected(reason), result.attempts.first().outcome)
        assertIs<AttemptOutcome.Accepted>(result.attempts.last().outcome)
        val retry = llm.requests.last()
        assertEquals(listOf(PreviousAttempt(listOf(wrong), reason)), retry.previousAttempts)
        assertEquals(0.3, retry.temperature)
        assertEquals(1, adapter.verifyCalls, "a rejected edit is never verified")
    }

    @Test
    fun `an attempt without progress is rejected and the working tree is reset to the last commit`() {
        val worse = Edit("App.java", "broken();", "broken();\n        missing();")
        val adapter = FakeAdapter()
        val llm = ScriptedLlm(edits(worse), edits(worse))

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        val expected =
            """
            no progress: 0 build errors, 1 compile errors, 0 test failures -> 0 build errors, 2 compile errors, 0 test failures
            Errors after the edits:
            - [javac] App.java:3: cannot find symbol broken
            - [javac] App.java:4: cannot find symbol missing
            """.trimIndent()
        assertEquals(AttemptOutcome.Rejected(expected), result.attempts.first().outcome)
        assertEquals(
            expected,
            llm.requests
                .last()
                .previousAttempts
                .single()
                .failure,
            "the retry learns what the edits caused",
        )
        assertEquals("", git("status", "--porcelain"))
        assertEquals(app, repo.resolve("App.java").readText())
        assertEquals("1", git("rev-list", "--count", "HEAD").trim())
        assertEquals(listOf(compileError(3)), result.verification.causedByUpdate, "the result is the last accepted state")
    }

    @Test
    fun `the errors after an attempt without progress are cut at ten, with the rest of each message`() {
        val many =
            List(12) { Diagnostic("App.java", it + 1, 9, "symbol:broken", "cannot find symbol\nsymbol: x$it", Severity.ERROR, "javac") }
        val adapter = FakeAdapter(problems = { dir -> if ("broken" in dir.resolve("App.java").readText()) many.take(1) else many })

        val result =
            loop(adapter, ScriptedLlm(edits(Edit("App.java", "broken", "other"))), Budgets(attempts = 1))
                .run(repo, context, adapter.initial())

        val reason = (result.attempts.single().outcome as AttemptOutcome.Rejected).reason
        assertContains(reason, "- [javac] App.java:10: cannot find symbol\n  symbol: x9\n- and 2 more")
        assertFalse("symbol: x10" in reason, reason)
    }

    @Test
    fun `an invalid answer, or one without edits, is a rejected attempt, and two of them in a row stop the loop`() {
        val adapter = FakeAdapter()

        val result = loop(adapter, ScriptedLlm({ Proposal.Invalid("not JSON") }, edits())).run(repo, context, adapter.initial())

        assertEquals(
            listOf(
                AttemptOutcome.Rejected("the model's answer is invalid: not JSON"),
                AttemptOutcome.Rejected("the model proposed no edits"),
            ),
            result.attempts.map { it.outcome },
        )
        assertEquals(StopReason.NO_PROGRESS, result.stopReason)
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

    @Test
    fun `fixing a build error is progress even if it unmasks many compile errors`() {
        val buildError = Diagnostic(null, null, null, "artifact:lib", "Could not find artifact", Severity.ERROR, "maven")
        val adapter =
            FakeAdapter(problems = { dir ->
                if ("unresolved" in dir.resolve("App.java").readText()) listOf(buildError) else List(50) { compileError(it + 1) }
            })
        repo.resolve("App.java").writeText("unresolved\n")
        git("commit", "--quiet", "-am", "unresolved")

        val result =
            loop(adapter, ScriptedLlm(edits(Edit("App.java", "unresolved", "resolved"))), Budgets(attempts = 1))
                .run(repo, context, adapter.initial())

        val accepted = assertIs<AttemptOutcome.Accepted>(result.attempts.first().outcome)
        assertEquals(Progress(1, 0, 0), accepted.before)
        assertEquals(Progress(0, 50, 0), accepted.after)
    }

    @Test
    fun `test failures caused by the update are fixed too, but never at the price of a compile error`() {
        val adapter =
            FakeAdapter(problems = { dir ->
                val text = dir.resolve("App.java").readText()
                when {
                    "compileError" in text -> {
                        listOf(compileError(1))
                    }

                    "failing" in text -> {
                        List(
                            3,
                        ) { Diagnostic("App.java", 1, null, "test", "expected 1", Severity.ERROR, "surefire", "AppTest#t$it") }
                    }

                    else -> {
                        emptyList()
                    }
                }
            })
        repo.resolve("App.java").writeText("failing\n")
        git("commit", "--quiet", "-am", "failing")
        val worse = Edit("App.java", "failing", "compileError")

        val result =
            loop(adapter, ScriptedLlm(edits(worse), edits(Edit("App.java", "failing", "passing"))), base = FakeBase())
                .run(repo, context, attribute(adapter, repo, FakeBase()))

        assertEquals(
            3,
            result.attempts
                .first()
                .diagnostics.size,
        )
        assertContains((result.attempts.first().outcome as AttemptOutcome.Rejected).reason, "-> 0 build errors, 1 compile errors")
        assertIs<AttemptOutcome.Accepted>(result.attempts.last().outcome)
        assertEquals(StopReason.NOTHING_LEFT, result.stopReason)
    }

    @Test
    fun `a cluster that used its attempts gives way to the next, and the loop stops when every cluster has`() {
        repo.resolve("App.java").writeText(app.replace("broken();", "broken();\n        broken();\n        missing();"))
        git("commit", "--quiet", "-am", "two clusters")
        val llm = ScriptedLlm(edits(Edit("App.java", "nowhere", "x")), edits(Edit("App.java", "nowhere", "x")))
        val adapter = FakeAdapter()

        val result = loop(adapter, llm, Budgets(attemptsPerCluster = 1, attemptsWithoutProgress = 10)).run(repo, context, adapter.initial())

        assertEquals(listOf("symbol:broken", "symbol:missing"), result.attempts.map { it.cluster })
        assertEquals(StopReason.CLUSTER_BUDGETS, result.stopReason)
    }

    @Test
    fun `the loop stops when the attempts in total are used up`() {
        val adapter = FakeAdapter()

        val result =
            loop(
                adapter,
                ScriptedLlm(edits(Edit("App.java", "nowhere", "x"))),
                Budgets(attempts = 1),
            ).run(repo, context, adapter.initial())

        assertEquals(1, result.attempts.size)
        assertEquals(StopReason.ATTEMPT_BUDGET, result.stopReason)
    }

    @Test
    fun `the loop stops when its time is up`() {
        val adapter = FakeAdapter()
        val slow =
            ScriptedLlm({
                time += 31.minutes
                Proposal.Edits(listOf(Edit("App.java", "nowhere", "x")), "slow")
            })

        val result = loop(adapter, slow).run(repo, context, adapter.initial())

        assertEquals(1, result.attempts.size)
        assertEquals(StopReason.TIME_BUDGET, result.stopReason)
    }

    @Test
    fun `an accepted attempt resets the count of attempts without progress`() {
        repo.resolve("App.java").writeText(app.replace("broken();", "broken();\n        broken(); // second"))
        git("commit", "--quiet", "-am", "two errors")
        val miss = edits(Edit("App.java", "nowhere", "x"))
        val llm = ScriptedLlm(miss, edits(Edit("App.java", "broken(); // second", "fixed();")), miss, miss)
        val adapter = FakeAdapter()

        val result = loop(adapter, llm, Budgets(attemptsPerCluster = 5)).run(repo, context, adapter.initial())

        assertEquals(4, result.attempts.size)
        assertEquals(StopReason.NO_PROGRESS, result.stopReason)
    }

    @Test
    fun `nothing caused by the update means no request at all`() {
        val llm = ScriptedLlm()

        val result = loop(FakeAdapter(), llm).run(repo, context, Verification())

        assertEquals(FixLoopResult(emptyList(), StopReason.NOTHING_LEFT, Verification()), result)
        assertEquals(emptyList(), llm.requests)
    }

    @Test
    fun `the commit subject is the first line of the rationale, or the cluster if the rationale is blank`() {
        repo.resolve("App.java").writeText(app.replace("broken();", "broken();\n        broken(); // second"))
        git("commit", "--quiet", "-am", "two errors")
        val adapter = FakeAdapter()
        val llm =
            ScriptedLlm(
                edits(Edit("App.java", "broken(); // second", "fixed();"), rationale = "Fix the second call\nand explain at length"),
                edits(fix, rationale = " "),
            )

        loop(adapter, llm).run(repo, context, adapter.initial())

        assertEquals("Fix symbol:broken\nFix the second call", git("log", "-2", "--format=%s").trim())
    }

    private fun loop(
        adapter: FakeAdapter,
        llm: LlmClient,
        budgets: Budgets = Budgets(),
        base: BaseCommit? = null,
    ) = FixLoop(adapter, llm, base, budgets, time)

    private fun edits(
        vararg edits: Edit,
        rationale: String = "Fix it",
    ): (FixRequest) -> Proposal = { Proposal.Edits(edits.toList(), rationale) }

    private fun compileError(line: Int) =
        Diagnostic("App.java", line, 9, "symbol:broken", "cannot find symbol broken", Severity.ERROR, "javac")

    private fun git(vararg args: String): String {
        val command = listOf("git", "-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
        val result = runProcess(command + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }

    /** Answers with the given functions in order, and records every request. */
    private class ScriptedLlm(
        vararg answers: (FixRequest) -> Proposal,
    ) : LlmClient {
        val requests = mutableListOf<FixRequest>()
        private val answers = ArrayDeque(answers.toList())

        override fun propose(request: FixRequest): Proposal {
            requests += request
            return answers.removeFirst()(request)
        }
    }

    /** A base checkout on which every test passes. */
    private inner class FakeBase : BaseCommit {
        override fun <T> checkout(block: (Path) -> T) = block(baseCheckout)
    }

    /**
     * By default, every line of `App.java` that calls `broken()` or `missing()` is a compile error, keyed by the symbol.
     * Versions are the `package version` lines of `pom.xml`, the build file. Tests fail on the head and pass on the base.
     */
    private inner class FakeAdapter(
        private val problems: (Path) -> List<Diagnostic> = ::compileErrors,
        private val versions: ((Path) -> Map<String, String>)? = { dir ->
            dir.resolve("pom.xml").readLines().associate { it.substringBefore(' ') to it.substringAfter(' ') }
        },
        override val languageSupport: LanguageSupport =
            LanguageSupport {
                file,
                content,
                ->
                if (file.endsWith(".java")) facts(content) else null
            },
    ) : EcosystemAdapter {
        var verifyCalls = 0

        override val ecosystem = "test"

        override fun isEditable(file: String) = true

        override fun testsRun(repo: Path): Int? = null

        override fun testDisablingConfig(
            file: String,
            content: String,
            line: Int,
        ): String? = null

        override val versionScheme = DottedVersions
        override val recipeRunner = null
        override val resolvedVersions = versions?.let { ResolvedVersions(it) }

        fun initial() = Verification(causedByUpdate = problems(repo))

        override fun detect(repo: Path) = true

        override fun verify(repo: Path): List<Diagnostic> {
            verifyCalls++
            return problems(repo)
        }

        override fun runTests(
            repo: Path,
            tests: List<Diagnostic>,
        ) = if (repo == baseCheckout) emptyList() else tests

        override fun ground(
            repo: Path,
            change: DependencyChange,
            diagnostics: List<Diagnostic>,
        ) = "API of ${change.`package`} ${change.toVersion} for ${diagnostics.size} diagnostics"

        override fun clusterKey(diagnostic: Diagnostic) = diagnostic.code

        override fun isBuildFile(file: String) = file == "pom.xml"
    }

    /** The methods (`void name(`) of class `App`, and every `return null` as a stub. */
    private fun facts(content: String): SourceFacts {
        val lines = content.lines().withIndex()
        return SourceFacts(
            methods =
                lines.mapNotNull { (index, line) ->
                    Regex("""void (\w+)\(""").find(line)?.let { Method("App", it.groupValues[1], LineRange(index + 1, index + 1)) }
                },
            stubs =
                lines.filter { "return null" in it.value }.map { (index, line) ->
                    Fragment(LineRange(index + 1, index + 1), line.trim())
                },
        )
    }

    private fun compileErrors(dir: Path): List<Diagnostic> =
        dir.resolve("App.java").readLines().withIndex().mapNotNull { (index, line) ->
            when {
                "broken()" in line -> {
                    compileError(index + 1)
                }

                "missing()" in line -> {
                    Diagnostic(
                        "App.java",
                        index + 1,
                        9,
                        "symbol:missing",
                        "cannot find symbol missing",
                        Severity.ERROR,
                        "javac",
                    )
                }

                else -> {
                    null
                }
            }
        }
}
