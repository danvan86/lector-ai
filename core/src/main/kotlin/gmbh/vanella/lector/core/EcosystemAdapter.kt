package gmbh.vanella.lector.core

import java.nio.file.Path

/** The extension point every ecosystem implements (Maven, Gradle, npm, …). It grows when a milestone needs more. */
interface EcosystemAdapter {
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
