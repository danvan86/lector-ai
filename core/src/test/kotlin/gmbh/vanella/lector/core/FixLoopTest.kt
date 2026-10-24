package gmbh.vanella.lector.core

import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class FixLoopTest : FixLoopFixture() {
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
        val rename = Edit("App.java", "void run()", "void start()")
        val overrides = Diagnostic("App.java", 2, 5, "override", "method does not override a method", Severity.ERROR, "javac")
        val adapter = FakeAdapter()

        val pointedInto =
            loop(adapter, ScriptedLlm(edits(rename)), Budgets(attempts = 1)).run(repo, context, Verification(listOf(overrides)))
        val verifiedOnce = adapter.verifyCalls
        val elsewhere = loop(adapter, ScriptedLlm(edits(rename)), Budgets(attempts = 1)).run(repo, context, adapter.initial())

        assertEquals(emptyList(), (pointedInto.attempts.single().outcome as AttemptOutcome.Rejected).violations, "verified, no progress")
        assertEquals(1, verifiedOnce)
        assertEquals(
            listOf("symbol-comparison"),
            (elsewhere.attempts.single().outcome as AttemptOutcome.Rejected).violations.map { it.rule },
        )
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
    fun `a build that runs past the time budget is stopped, its attempt rejected and reset, and the loop stops on time`() {
        val timeouts = mutableListOf<Duration>()
        val adapter =
            FakeAdapter(onVerify = { timeout ->
                timeouts += timeout
                time += timeout
                throw ProcessTimedOut("mvn did not finish within $timeout")
            })

        val result = loop(adapter, ScriptedLlm(edits(fix))).run(repo, context, adapter.initial())

        assertEquals(listOf(30.minutes), timeouts, "the build gets the time that is left")
        assertEquals(StopReason.TIME_BUDGET, result.stopReason)
        assertEquals(
            AttemptOutcome.Rejected("the builds ran past the time budget: mvn did not finish within 30m"),
            result.attempts.single().outcome,
        )
        assertEquals(app, repo.resolve("App.java").readText())
        assertEquals("", git("status", "--porcelain"))
    }

    @Test
    fun `a build that hits the adapter's own limit while time is left is still a tool error`() {
        val adapter = FakeAdapter(onVerify = { throw ProcessTimedOut("mvn did not finish within 1h") })

        val error = assertFailsWith<ProcessTimedOut> { loop(adapter, ScriptedLlm(edits(fix))).run(repo, context, adapter.initial()) }

        assertEquals("mvn did not finish within 1h", error.message)
        assertEquals(app, repo.resolve("App.java").readText())
    }

    @Test
    fun `the first request on a cluster has temperature 0 and everything the model needs`() {
        val llm = ScriptedLlm(edits(fix))
        val adapter = FakeAdapter()

        loop(adapter, llm).run(repo, context, adapter.initial())

        val request = llm.requests.single()
        assertEquals(listOf(change), request.changes)
        assertEquals(listOf(compileError(3)), request.diagnostics)
        assertEquals(listOf(Snippet("App.java", 1, 5, app)), request.snippets)
        assertEquals("API of $lib 2.0 for 1 diagnostics", request.grounding)
        assertEquals("2.0 renames broken() to fixed()", request.releaseNotes)
        assertEquals("diff --git a/pom.xml b/pom.xml", request.recipeDiff)
        assertEquals(emptyList(), request.previousAttempts)
        assertEquals(0.0, request.temperature)
    }

    @Test
    fun `with several changes, the request names them all and grounds each, and commits use the given identity`() {
        val other = DependencyChange("test", "org.example:other", "2.0", "3.0", UpdateType.MAJOR)
        val llm = ScriptedLlm(edits(fix))
        val adapter = FakeAdapter()
        val bot = CommitIdentity("lector-ai[bot]", "1+lector-ai[bot]@users.noreply.github.com")

        FixLoop(
            adapter,
            llm,
            null,
            identity = bot,
            timeSource = time,
        ).run(repo, context.copy(changes = listOf(change, other)), adapter.initial())

        val request = llm.requests.single()
        assertEquals(listOf(change, other), request.changes)
        assertEquals("API of $lib 2.0 for 1 diagnostics\n\nAPI of org.example:other 3.0 for 1 diagnostics", request.grounding)
        assertEquals("${bot.name} <${bot.email}>", git("log", "-1", "--format=%an <%ae>").trim())
    }

    @Test
    fun `a change without grounding adds nothing to the request's grounding`() {
        val llm = ScriptedLlm(edits(fix))
        val adapter = FakeAdapter(grounding = { change -> if (change.`package` == lib) "" else "API of ${change.`package`}" })
        val other = DependencyChange("test", "org.example:other", "2.0", "3.0", UpdateType.MAJOR)

        loop(adapter, llm).run(repo, context.copy(changes = listOf(change, other)), adapter.initial())

        assertEquals("API of org.example:other", llm.requests.single().grounding)
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
    fun `a cluster that doesn't fit is sent with the diagnostics that fit, each with its code, and the rest come back next round`() {
        val code = app.replace("broken();", "broken(); // a\n        broken(); // b\n        broken(); // c")
        repo.resolve("App.java").writeText(code)
        git("commit", "--quiet", "-am", "three errors")
        val llm =
            ScriptedLlm(
                edits(Edit("App.java", "broken(); // a", "fixed();")),
                edits(Edit("App.java", "broken(); // b\n        broken(); // c", "fixed();")),
                fits = { it.diagnostics.size <= 2 },
            )
        val adapter = FakeAdapter()

        val result = loop(adapter, llm).run(repo, context, adapter.initial())

        val first = llm.requests.first()
        assertEquals(listOf(compileError(3), compileError(4)), first.diagnostics)
        assertEquals(listOf(Snippet("App.java", 1, 7, code)), first.snippets)
        assertEquals("API of $lib 2.0 for 2 diagnostics", first.grounding)
        assertEquals(listOf(1, 0), result.attempts.map { it.diagnosticsLeftOut })
        assertEquals(listOf(compileError(3), compileError(4)), result.attempts.first().diagnostics)
        assertEquals(1, result.requestsWithDiagnosticsLeftOut)
        assertEquals(StopReason.NOTHING_LEFT, result.stopReason)
        assertEquals(
            "Addresses:\n- [javac] App.java:3: cannot find symbol broken\n- [javac] App.java:4: cannot find symbol broken",
            git("log", "-1", "--skip=1", "--format=%b").trim(),
        )
    }

    @Test
    fun `a single diagnostic that doesn't fit on its own is still sent, alone`() {
        repo.resolve("App.java").writeText(app.replace("broken();", "broken(); // a\n        broken(); // b\n        broken(); // c"))
        git("commit", "--quiet", "-am", "three errors")
        val llm = ScriptedLlm(edits(Edit("App.java", "nowhere", "x")), fits = { false })
        val adapter = FakeAdapter()

        val result = loop(adapter, llm, Budgets(attempts = 1)).run(repo, context, adapter.initial())

        assertEquals(listOf(compileError(3)), llm.requests.single().diagnostics)
        assertEquals(2, result.attempts.single().diagnosticsLeftOut)
        assertEquals(1, result.requestsWithDiagnosticsLeftOut)
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
    fun `a model that doesn't answer in time ends the loop with a report, and its attempt is rejected`() {
        val adapter = FakeAdapter()
        val silent = ScriptedLlm({ Proposal.Unanswered("Ollama at http://ollama:11434 did not answer within 15m") })

        val result = loop(adapter, silent).run(repo, context, adapter.initial())

        assertEquals(StopReason.MODEL_UNANSWERED, result.stopReason, "the next request would wait as long")
        assertEquals(
            AttemptOutcome.Rejected("the model didn't answer: Ollama at http://ollama:11434 did not answer within 15m"),
            result.attempts.single().outcome,
        )
        assertEquals(adapter.initial(), result.verification)
        assertEquals("", git("status", "--porcelain"))
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
}
