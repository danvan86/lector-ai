package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.time.Duration

/** The extension point every ecosystem implements, one adapter per build tool. It grows when a milestone needs more. */
interface EcosystemAdapter {
    /** The package namespace this adapter's packages live in, e.g. `npm` or `pypi`. One per namespace, not per build tool. */
    val ecosystem: String

    val versionScheme: VersionScheme

    /** Runs recipes on a working tree. Optional: null if the ecosystem has none. */
    val recipeRunner: RecipeRunner?

    /** Reads the effective versions of a working tree. Optional: null if the ecosystem can't. */
    val resolvedVersions: ResolvedVersions?

    /** The facts about source files that the guardrails check. */
    val languageSupport: LanguageSupport

    /** Whether this adapter handles the repository. */
    fun detect(repo: Path): Boolean

    /**
     * Builds the repository and runs its tests. Returns every problem found; empty means green. A build that runs longer
     * than [timeout] is stopped with a [ProcessTimedOut].
     */
    fun verify(
        repo: Path,
        timeout: Duration,
    ): List<Diagnostic>

    /** The number of tests the last [verify] of [repo] ran, or null if it can't tell, e.g. because no test ran. */
    fun testsRun(repo: Path): Int?

    /**
     * Runs only the tests that the given test failures come from. Returns the problems of that run: the tests that
     * still fail, and any build problem that kept the tests from running. A run longer than [timeout] is stopped with a
     * [ProcessTimedOut].
     */
    fun runTests(
        repo: Path,
        tests: List<Diagnostic>,
        timeout: Duration,
    ): List<Diagnostic>

    /**
     * The API surface of [change]'s new version that [diagnostics] need (signatures, types, schemas), as text for the
     * model. Empty if there is none. Never downloads anything: it reads what the build already resolved.
     */
    fun ground(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ): String

    /**
     * The root cause [diagnostic] points to, e.g. a missing symbol, package, or artifact, or null if it names none.
     * Diagnostics with the same key are fixed together.
     */
    fun clusterKey(diagnostic: Diagnostic): String?

    /** Whether [file] (relative to the repository root, `/` as separator) is a build file, e.g. `pom.xml`. */
    fun isBuildFile(file: String): Boolean

    /**
     * The editable scope: whether the model may edit [file] (relative to the repository root, `/` as separator). False
     * for build output and generated code (rule 5).
     */
    fun isEditable(file: String): Boolean

    /** What in [line] (1-based) of the build file [file] with [content] disables tests, as a reason for the model, or null. */
    fun testDisablingConfig(
        file: String,
        content: String,
        line: Int,
    ): String?

    /**
     * What the change of the build file [file] from [before] to [after] loosens as a whole, as reasons for the model: what
     * no added line shows, like a removed setting or a narrowed list. Empty if nothing. Maven and Gradle run every test and
     * check by default, so removing a setting loosens nothing there; TypeScript's `test` script and `strict` are opt-in.
     */
    fun loosening(
        file: String,
        before: String,
        after: String,
    ): List<String>

    /**
     * Brings the files the build tool derives from the build files, like a lockfile, in line with [repo]'s working tree
     * after an attempt edited a build file. The files it changed go into the attempt's commit. A run longer than [timeout]
     * is stopped with a [ProcessTimedOut].
     */
    fun updateLockfiles(
        repo: Path,
        timeout: Duration,
    ): LockfileUpdate
}

/** What [EcosystemAdapter.updateLockfiles] did. */
sealed interface LockfileUpdate {
    /** The derived [files] it changed, relative to the repository root. */
    data class Updated(
        val files: List<String>,
    ) : LockfileUpdate

    /** The build tool couldn't follow the edited build files, e.g. because a version doesn't exist. */
    data class Failed(
        val reason: String,
    ) : LockfileUpdate
}
