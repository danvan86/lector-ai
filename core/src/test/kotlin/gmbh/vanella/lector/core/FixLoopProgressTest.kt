package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * M11 section 1: an attempt that removes the compile errors it was sent and only uncovers errors that were hidden behind
 * them is progress, even if the counts stay equal. The messages are javac's (evidence.md, flyway-9).
 */
class FixLoopProgressTest : FixLoopFixture() {
    private val factory = "FlywayFactory.java"

    // Flyway 9 removed all four methods. javac reports one error per call chain: the first one it can't resolve.
    private val removed = listOf("ignoreFutureMigrations", "ignoreIgnoredMigrations", "ignoreMissingMigrations", "ignorePendingMigrations")
    private val chain =
        """
        class FlywayFactory {
            FluentConfiguration build() {
                return configure()
                      .ignoreFutureMigrations(ignoreFutureMigrations)
                      .ignoreIgnoredMigrations(ignoreIgnoredMigrations)
                      .ignoreMissingMigrations(ignoreMissingMigrations)
                      .ignorePendingMigrations(ignorePendingMigrations)
                      .load();
            }
        }
        """.trimIndent() + "\n"

    // flyway-9, qwen3-coder:30b: the right replacement for the first call (evidence.md).
    private val replaceFuture =
        Edit(
            factory,
            "              .ignoreFutureMigrations(ignoreFutureMigrations)\n",
            "              .ignoreMigrationPatterns(\"future\")\n",
        )

    @Test
    fun `a fix that uncovers the next removed method in the same call chain is kept, with equal counts`() {
        commit(factory to chain)
        val adapter = FakeAdapter(problems = ::chainErrors)

        val result = loop(adapter, ScriptedLlm(edits(replaceFuture)), Budgets(attempts = 1)).run(repo, context, adapter.initial())

        val accepted = assertIs<AttemptOutcome.Accepted>(result.attempts.single().outcome)
        assertEquals(Progress(0, 1, 0), accepted.before)
        assertEquals(Progress(0, 1, 0), accepted.after)
        assertEquals(git("rev-parse", "HEAD").trim(), accepted.commit)
        assertTrue(".ignoreMigrationPatterns(\"future\")" in repo.resolve(factory).readText(), "the fix is committed")
        assertEquals(
            listOf(removedMethod("ignoreIgnoredMigrations", 5)),
            result.verification.causedByUpdate,
            "the uncovered error is left for the next attempt",
        )
    }

    @Test
    fun `a fix whose new error lies in a line it changed is rejected`() {
        commit(factory to chain)
        val adapter = FakeAdapter(problems = ::chainErrors)
        // flyway-9, qwen2.5-coder:7b: another removed method, in the line it replaced (evidence.md).
        val wrong = Edit(factory, "ignoreFutureMigrations(ignoreFutureMigrations)", "ignoreMissingMigrations(ignoreFutureMigrations)")

        val result = loop(adapter, ScriptedLlm(edits(wrong)), Budgets(attempts = 1)).run(repo, context, adapter.initial())

        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts.single().outcome)
        assertTrue(rejected.reason.startsWith("no progress"), rejected.reason)
        assertEquals(chain, repo.resolve(factory).readText())
    }

    @Test
    fun `an error that was there before and is still there in a changed line doesn't block a fix`() {
        val calls =
            """
            class Calls {
                Object build() {
                    return api
                          .gone(1)
                          .gone("x")
                          .hidden(true);
                }
            }
            """.trimIndent() + "\n"
        commit("Calls.java" to calls)
        val adapter = FakeAdapter(problems = ::callErrors)
        // Only the first error fits into the request; the edit spans the second one's line and leaves it as it is.
        val llm =
            ScriptedLlm(
                edits(Edit("Calls.java", ".gone(1)\n              .gone(\"x\")", ".kept(1)\n              .gone(\"x\")")),
                fits = { it.diagnostics.size <= 1 },
            )

        val result = loop(adapter, llm, Budgets(attempts = 1)).run(repo, context, adapter.initial())

        assertEquals(
            1,
            llm.requests
                .single()
                .diagnostics.size,
            "one of the two errors was sent",
        )
        val accepted = assertIs<AttemptOutcome.Accepted>(result.attempts.single().outcome)
        assertEquals(Progress(0, 2, 0), accepted.before)
        assertEquals(Progress(0, 2, 0), accepted.after)
    }

    @Test
    fun `a fix after which a new build error without a location takes the place of another is rejected`() {
        commit(factory to chain)
        val enforcer =
            buildError(
                "Failed to execute goal org.apache.maven.plugins:maven-enforcer-plugin:3.5.0:enforce (enforce-versions) on project app: " +
                    "Rule 0: org.apache.maven.enforcer.rules.version.RequireJavaVersion failed with message:",
            )
        val gpg =
            buildError(
                "Failed to execute goal org.apache.maven.plugins:maven-gpg-plugin:1.6:sign (sign-artifacts) on project dledger-all: " +
                    "Unable to execute gpg command: Error while executing process. Cannot run program \"gpg\": error=2, " +
                    "No such file or directory -> [Help 1]",
            )
        val adapter =
            FakeAdapter(problems = { dir ->
                val fixed = "ignoreMigrationPatterns" in dir.resolve(factory).readText()
                listOf(if (fixed) gpg else enforcer) + chainErrors(dir)
            })
        // The build error's cluster goes first and uses its one attempt; then the chain's cluster gets the right fix.
        val llm = ScriptedLlm(edits(Edit(factory, "      .load();", "      .load(); // try")), edits(replaceFuture))

        val result =
            loop(
                adapter,
                llm,
                Budgets(attemptsPerCluster = 1, attempts = 2, attemptsWithoutProgress = 3),
            ).run(repo, context, adapter.initial())

        assertEquals(listOf(enforcer.message), llm.requests[0].diagnostics.map { it.message }, "the build error went first")
        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts[1].outcome, "1/1/0 -> 1/1/0, but GPG is a new build error")
        assertTrue(rejected.reason.startsWith("no progress"), rejected.reason)
    }

    @Test
    fun `an edit that only moves its error to another line is rejected`() {
        commit(factory to chain)
        val adapter = FakeAdapter(problems = ::chainErrors)
        val above = Edit(factory, "        return configure()\n", "        // configure the migrations\n        return configure()\n")

        val result = loop(adapter, ScriptedLlm(edits(above)), Budgets(attempts = 1)).run(repo, context, adapter.initial())

        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts.single().outcome, "the same error, one line further down")
        assertTrue(rejected.reason.startsWith("no progress"), rejected.reason)
    }

    @Test
    fun `a fix after which more errors are left than before is rejected`() {
        commit(factory to chain, "Other.java" to "class Other {\n    int x = gone();\n}\n")
        val elsewhere =
            Diagnostic("Other.java", 2, 13, "symbol:gone", "cannot find symbol\n  symbol:   method gone()", Severity.ERROR, "javac")
        val adapter =
            FakeAdapter(problems = { dir ->
                chainErrors(dir) + if ("ignoreMigrationPatterns" in dir.resolve(factory).readText()) listOf(elsewhere) else emptyList()
            })

        val result = loop(adapter, ScriptedLlm(edits(replaceFuture)), Budgets(attempts = 1)).run(repo, context, adapter.initial())

        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts.single().outcome)
        assertTrue(rejected.reason.startsWith("no progress"), rejected.reason)
    }

    @Test
    fun `a fix that makes its failing test pass and another test fail is rejected`() {
        commit("Calc.java" to "class Calc {\n    int add(int a, int b) {\n        return a - b;\n    }\n}\n")
        val adds = testFailure("com.example.CalcTest#adds", "expected: <3> but was: <-1>")
        val subtracts = testFailure("com.example.CalcTest#subtracts", "expected: <-1> but was: <3>")
        val adapter =
            FakeAdapter(problems = { dir ->
                val calc = dir.resolve("Calc.java").readText()
                listOfNotNull(adds.takeIf { "a - b" in calc }, subtracts.takeIf { "a + b" in calc })
            })
        val swap = Edit("Calc.java", "return a - b;", "return a + b;")

        val result =
            loop(adapter, ScriptedLlm(edits(swap)), Budgets(attempts = 1), base = FakeBase())
                .run(repo, context, Verification(causedByUpdate = listOf(adds)))

        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts.single().outcome)
        assertTrue(rejected.reason.startsWith("no progress"), rejected.reason)
    }

    @Test
    fun `an error that an earlier accepted fix removed and that comes back is not uncovered`() {
        commit(
            "Swap.java" to "class Swap {\n    {\n        x();\n    }\n}\n",
            "Other.java" to "class Other {\n    {\n        y = 1;\n    }\n}\n",
        )
        val x = Diagnostic("Swap.java", 3, 9, "symbol:x", "cannot find symbol\n  symbol:   method x()", Severity.ERROR, "javac")
        val y = Diagnostic("Other.java", 3, 9, "symbol:y", "cannot find symbol\n  symbol:   variable y", Severity.ERROR, "javac")
        val adapter =
            FakeAdapter(problems = { dir ->
                val swap = dir.resolve("Swap.java").readText()
                val other = dir.resolve("Other.java").readText()
                listOfNotNull(
                    x.takeIf { "x();" in swap || "y = 2;" in other },
                    y.takeIf { "x2();" in swap && "y = 1;" in other },
                )
            })
        val llm = ScriptedLlm(edits(Edit("Swap.java", "x();", "x2();")), edits(Edit("Other.java", "y = 1;", "y = 2;")))

        val result = loop(adapter, llm, Budgets(attempts = 2)).run(repo, context, adapter.initial())

        assertIs<AttemptOutcome.Accepted>(result.attempts[0].outcome, "x is gone, y was hidden behind it")
        val rejected = assertIs<AttemptOutcome.Rejected>(result.attempts[1].outcome, "y is gone, but x is back")
        assertTrue(rejected.reason.startsWith("no progress"), rejected.reason)
    }

    private fun commit(vararg files: Pair<String, String>) {
        files.forEach { (name, content) -> repo.resolve(name).writeText(content) }
        git("add", ".")
        git("commit", "--quiet", "-m", "more code the update broke")
    }

    /** javac on the chain: the first call to a removed method, and nothing after it. */
    private fun chainErrors(dir: Path): List<Diagnostic> =
        dir
            .resolve(factory)
            .readText()
            .lines()
            .withIndex()
            .firstNotNullOfOrNull { (index, line) ->
                removed.firstOrNull { ".$it(" in line }?.let { removedMethod(it, index + 1) }
            }.let(::listOfNotNull)

    private fun removedMethod(
        name: String,
        line: Int,
    ) = Diagnostic(
        factory,
        line,
        15,
        "symbol:$name",
        "cannot find symbol\n  symbol:   method $name(boolean)\n  location: class org.flywaydb.core.api.configuration.FluentConfiguration",
        Severity.ERROR,
        "javac",
    )

    /** `gone(int)` and `gone(String)` are removed; `hidden(boolean)` too, but javac only gets to it once `gone(1)` is fixed. */
    private fun callErrors(dir: Path): List<Diagnostic> {
        val lines = dir.resolve("Calls.java").readText().lines()

        fun error(
            marker: String,
            method: String,
        ): Diagnostic? {
            val index = lines.indexOfFirst { marker in it }
            if (index < 0) return null
            val message = "cannot find symbol\n  symbol:   method $method\n  location: class Api"
            return Diagnostic("Calls.java", index + 1, 23, "symbol:gone", message, Severity.ERROR, "javac")
        }
        val first = error(".gone(1)", "gone(int)")
        val second = error(".gone(\"x\")", "gone(java.lang.String)")
        return listOfNotNull(first, second, if (first == null) error(".hidden(true)", "hidden(boolean)") else null)
    }

    private fun buildError(message: String) = Diagnostic(null, null, null, null, message, Severity.ERROR, "maven")

    private fun testFailure(
        test: String,
        message: String,
    ) = Diagnostic(null, null, null, null, "org.opentest4j.AssertionFailedError: $message", Severity.ERROR, "surefire", test)
}
