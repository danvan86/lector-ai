package gmbh.vanella.lector.core

import java.nio.file.Path

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

    /** Builds the repository and runs its tests. Returns every problem found; empty means green. */
    fun verify(repo: Path): List<Diagnostic>

    /** The number of tests the last [verify] of [repo] ran, or null if it can't tell, e.g. because no test ran. */
    fun testsRun(repo: Path): Int?

    /**
     * Runs only the tests that the given test failures come from. Returns the problems of that run: the tests that
     * still fail, and any build problem that kept the tests from running.
     */
    fun runTests(
        repo: Path,
        tests: List<Diagnostic>,
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
}
