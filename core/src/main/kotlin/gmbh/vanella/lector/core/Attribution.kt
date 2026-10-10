package gmbh.vanella.lector.core

import org.slf4j.LoggerFactory
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("gmbh.vanella.lector.core.Attribution")

/** The commit the update is compared against. [checkout] runs `block` on a temporary checkout and removes it afterwards. */
fun interface BaseCommit {
    fun checkout(block: (Path) -> List<Diagnostic>): List<Diagnostic>
}

/**
 * Verifies [repo] and decides which problems the update caused (concept section 5, "Attribution").
 *
 * - Problems that aren't test failures (compile and build errors) are always caused by the update.
 * - Failing tests are rerun once. A test that passes on the rerun is flaky.
 * - Tests that still fail run on the [base] commit, and only those. Failing there too means pre-existing, passing there
 *   means caused by the update. Without a base, or if the base doesn't build, they are unattributed.
 */
fun attribute(
    adapter: EcosystemAdapter,
    repo: Path,
    base: BaseCommit?,
): Verification {
    val (tests, buildProblems) = adapter.verify(repo).partition { it.test != null }
    val stillFailing =
        if (tests.isEmpty()) {
            tests
        } else {
            val failedAgain = failedTests(adapter.runTests(repo, tests), "rerun")
            tests.filter { failedAgain == null || it.test in failedAgain }
        }
    val flaky = tests - stillFailing.toSet()
    val failedOnBase =
        if (stillFailing.isEmpty() || base == null) {
            null
        } else {
            failedTests(base.checkout { adapter.runTests(it, stillFailing) }, "base commit")
        }
    if (failedOnBase == null) {
        return Verification(causedByUpdate = buildProblems, flaky = flaky, unattributed = stillFailing)
    }
    val (preExisting, broken) = stillFailing.partition { it.test in failedOnBase }
    return Verification(causedByUpdate = buildProblems + broken, preExisting = preExisting, flaky = flaky)
}

/**
 * The names of the tests that failed in [run]. Null if the run had a build problem: then the tests may not have run at
 * all, and the run says nothing about them.
 */
private fun failedTests(
    run: List<Diagnostic>,
    runName: String,
): Set<String>? {
    val buildProblems = run.filter { it.test == null }
    if (buildProblems.isNotEmpty()) {
        logger.warn("The {} didn't build, so it says nothing about the failing tests: {}", runName, buildProblems)
        return null
    }
    return run.mapNotNull { it.test }.toSet()
}
