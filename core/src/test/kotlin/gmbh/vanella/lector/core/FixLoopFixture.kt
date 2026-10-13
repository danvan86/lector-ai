package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The repository, the fake adapter and model, and the helpers of the fix-loop tests. */
open class FixLoopFixture {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var baseCheckout: Path

    protected val lib = "org.example:lib"
    protected val change = DependencyChange("test", lib, "1.0", "2.0", UpdateType.MAJOR)
    protected val context =
        FixContext(listOf(change), releaseNotes = "2.0 renames broken() to fixed()", recipeDiff = "diff --git a/pom.xml b/pom.xml")
    protected val time = TestTimeSource()
    protected val fix = Edit("App.java", "broken();", "fixed();")
    protected val app =
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

    protected fun loop(
        adapter: FakeAdapter,
        llm: LlmClient,
        budgets: Budgets = Budgets(),
        base: BaseCommit? = null,
    ) = FixLoop(adapter, llm, base, budgets, timeSource = time)

    protected fun edits(
        vararg edits: Edit,
        rationale: String = "Fix it",
    ): (FixRequest) -> Proposal = { Proposal.Edits(edits.toList(), rationale) }

    protected fun compileError(line: Int) =
        Diagnostic("App.java", line, 9, "symbol:broken", "cannot find symbol broken", Severity.ERROR, "javac")

    protected fun git(vararg args: String): String {
        val command = listOf("git", "-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
        val result = runProcess(command + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }

    /** Answers with the given functions in order, and records every request. */
    protected class ScriptedLlm(
        vararg answers: (FixRequest) -> Proposal,
        private val fits: (FixRequest) -> Boolean = { true },
    ) : LlmClient {
        val requests = mutableListOf<FixRequest>()
        private val answers = ArrayDeque(answers.toList())

        override fun fits(request: FixRequest) = fits.invoke(request)

        override fun propose(request: FixRequest): Proposal {
            requests += request
            return answers.removeFirst()(request)
        }
    }

    /** A base checkout on which every test passes. */
    protected inner class FakeBase : BaseCommit {
        override fun <T> checkout(block: (Path) -> T) = block(baseCheckout)
    }

    /**
     * By default, every line of `App.java` that calls `broken()` or `missing()` is a compile error, keyed by the symbol.
     * Versions are the `package version` lines of `pom.xml`, the build file. Tests fail on the head and pass on the base.
     */
    protected inner class FakeAdapter(
        private val problems: (Path) -> List<Diagnostic> = ::compileErrors,
        private val grounding: (DependencyChange) -> String? = { null },
        private val onVerify: (timeout: Duration) -> Unit = {},
        versions: ((Path) -> Map<String, String>)? = { dir ->
            dir.resolve("pom.xml").readLines().associate { it.substringBefore(' ') to it.substringAfter(' ') }
        },
        private val lockfiles: ((Path) -> LockfileUpdate)? = null,
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

        override fun verify(
            repo: Path,
            timeout: Duration,
        ): List<Diagnostic> {
            verifyCalls++
            onVerify(timeout)
            return problems(repo)
        }

        override fun runTests(
            repo: Path,
            tests: List<Diagnostic>,
            timeout: Duration,
        ) = if (repo == baseCheckout) emptyList() else tests

        override fun ground(
            repo: Path,
            change: DependencyChange,
            diagnostics: List<Diagnostic>,
        ) = grounding(change) ?: "API of ${change.`package`} ${change.toVersion} for ${diagnostics.size} diagnostics"

        override fun clusterKey(diagnostic: Diagnostic) = diagnostic.code

        override fun isBuildFile(file: String) = file == "pom.xml"

        override fun updateLockfiles(
            repo: Path,
            timeout: Duration,
        ) = lockfiles?.invoke(repo) ?: LockfileUpdate.Updated(emptyList())
    }

    /** The methods (`void name(`) of class `App`, with the next line as body, and every `return null` as a stub. */
    private fun facts(content: String): SourceFacts {
        val lines = content.lines().withIndex()
        return SourceFacts(
            methods =
                lines.mapNotNull { (index, line) ->
                    Regex("""void (\w+)\(""").find(line)?.let {
                        Method("App", it.groupValues[1], LineRange(index + 1, index + 1), content.lines()[index + 1].trim())
                    }
                },
            stubs =
                lines.filter { "return null" in it.value }.map { (index, line) ->
                    Fragment(LineRange(index + 1, index + 1), line.trim())
                },
        )
    }

    protected fun compileErrors(dir: Path): List<Diagnostic> =
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
