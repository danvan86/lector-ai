package gmbh.vanella.lector.core

import java.nio.file.Path

/** The extension point every ecosystem implements, one adapter per build tool. It grows when a milestone needs more. */
interface EcosystemAdapter {
    /** The package namespace this adapter's packages live in, e.g. `maven`. */
    val ecosystem: String

    val versionScheme: VersionScheme

    /** Runs recipes on a working tree. Optional: null if the ecosystem has none. */
    val recipeRunner: RecipeRunner?

    /** Reads the effective versions of a working tree. Optional: null if the ecosystem can't. */
    val resolvedVersions: ResolvedVersions?

    /** Whether this adapter handles the repository. */
    fun detect(repo: Path): Boolean

    /** Builds the repository and runs its tests. Returns every problem found; empty means green. */
    fun verify(repo: Path): List<Diagnostic>

    /**
     * Runs only the tests that the given test failures come from. Returns the problems of that run: the tests that
     * still fail, and any build problem that kept the tests from running.
     */
    fun runTests(
        repo: Path,
        tests: List<Diagnostic>,
    ): List<Diagnostic>
}
