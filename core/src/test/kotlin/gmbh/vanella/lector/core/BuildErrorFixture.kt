package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.time.Duration

/**
 * Harness for the M11 tests of build errors on the base commit: an adapter whose head and base checkouts verify
 * differently, and a base that counts its checkouts. No assertions here.
 */
open class BuildErrorFixture : FixLoopFixture() {
    /** Maven's real message when `maven-gpg-plugin` finds no `gpg` (evidence.md, dledger). */
    protected val gpg =
        buildError(
            "Failed to execute goal org.apache.maven.plugins:maven-gpg-plugin:1.6:sign (sign-artifacts) on project dledger-all: " +
                "Unable to execute gpg command: Error while executing process. Cannot run program \"gpg\": error=2, " +
                "No such file or directory -> [Help 1]",
        )

    /** The same failure when the plugin runs `gpg` in a directory: the JDK names it, so the message carries the checkout's path. */
    protected fun gpgIn(directory: Path) =
        buildError(
            "Failed to execute goal org.apache.maven.plugins:maven-gpg-plugin:3.2.7:sign (sign-artifacts) on project app: " +
                "Unable to execute gpg command: Error while executing process. Cannot run program \"gpg\" " +
                "(in directory \"$directory\"): error=2, No such file or directory -> [Help 1]",
        )

    protected fun loop(
        adapter: TwoCheckoutAdapter,
        llm: LlmClient,
        budgets: Budgets = Budgets(),
        base: BaseCommit? = null,
    ) = FixLoop(adapter, llm, base, budgets, timeSource = time)

    protected fun buildError(message: String) = Diagnostic(null, null, null, null, message, Severity.ERROR, "maven")

    /**
     * The head verifies with [head], the base checkout with [base] (which may throw). Failing tests fail again on the
     * head and pass on the base. [testsRun] is what the head's last verify ran.
     */
    protected inner class TwoCheckoutAdapter(
        private val head: (Path) -> List<Diagnostic> = ::compileErrors,
        private val base: (Path) -> List<Diagnostic> = { emptyList() },
        private val testsRun: Int? = 12,
    ) : EcosystemAdapter {
        var baseVerifications = 0

        override val ecosystem = "test"
        override val languageSupport = LanguageSupport { _, _ -> null }
        override val versionScheme = DottedVersions
        override val recipeRunner = null
        override val resolvedVersions = null

        override fun detect(repo: Path) = true

        override fun verify(
            repo: Path,
            timeout: Duration,
        ): List<Diagnostic> =
            if (repo == this@BuildErrorFixture.repo) {
                head(repo)
            } else {
                baseVerifications++
                base(repo)
            }

        override fun runTests(
            repo: Path,
            tests: List<Diagnostic>,
            timeout: Duration,
        ) = if (repo == this@BuildErrorFixture.repo) tests else emptyList()

        override fun testsRun(repo: Path) = testsRun

        override fun ground(
            repo: Path,
            change: DependencyChange,
            diagnostics: List<Diagnostic>,
        ) = ""

        override fun clusterKey(diagnostic: Diagnostic) = diagnostic.code

        override fun isEditable(file: String) = true

        override fun isBuildFile(file: String) = file == "pom.xml"

        override fun testDisablingConfig(
            file: String,
            content: String,
            line: Int,
        ): String? = null

        override fun loosening(
            file: String,
            before: String,
            after: String,
        ) = emptyList<String>()

        override fun updateLockfiles(
            repo: Path,
            timeout: Duration,
        ) = LockfileUpdate.Updated(emptyList())
    }

    /** A base commit checked out at [checkout], which may be a link to the real directory, or whose checkout throws [failure]. */
    protected class CountingBase(
        private val checkout: Path,
        private val failure: Throwable? = null,
    ) : BaseCommit {
        var checkouts = 0

        override fun <T> checkout(block: (Path) -> T): T {
            checkouts++
            failure?.let { throw it }
            return block(checkout)
        }
    }
}
