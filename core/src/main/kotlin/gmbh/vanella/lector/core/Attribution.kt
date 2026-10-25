package gmbh.vanella.lector.core

import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.time.Duration

private val logger = LoggerFactory.getLogger("gmbh.vanella.lector.core.Attribution")

/** The commit the update is compared against. [checkout] runs `block` on a temporary checkout and removes it afterwards. */
interface BaseCommit {
    fun <T> checkout(block: (Path) -> T): T
}

/**
 * The base commit of one run, handed to every attribution of the run. The first build error without a location that
 * needs the base verifies it, in a separate checkout; what that found, a failure included, is reused for the rest of
 * the run. A base that fails to verify (a tool error, a timeout) never ends the run: its build errors are unknown.
 */
class RunBase(
    private val base: BaseCommit,
) : BaseCommit {
    // The base's build errors without a location, the checkout's path replaced; empty if the base failed to verify.
    private var buildErrors: Set<ErrorKey>? = null

    override fun <T> checkout(block: (Path) -> T): T = base.checkout(block)

    /** The build errors without a location of the base, verified by [adapter] the first time, in at most [timeout]. */
    internal fun buildErrors(
        adapter: EcosystemAdapter,
        timeout: Duration,
    ): Set<ErrorKey> =
        buildErrors ?: try {
            base.checkout { dir ->
                adapter
                    .verify(dir, timeout)
                    .filter(::isBuildError)
                    .map { pathless(it, dir) }
                    .toSet()
            }
        } catch (e: ToolError) {
            logger.warn("The base commit couldn't be verified, so its build errors are unknown: {}", e.message)
            emptySet()
        }.also { buildErrors = it }
}

/**
 * Verifies [repo] and decides which problems the update caused (concept section 5, "Attribution").
 *
 * - Compile errors are always caused by the update.
 * - A build error without a location is pre-existing if the [base] commit has the same one, the checkout's path aside,
 *   and the head's verify ran tests. Otherwise it is caused by the update: a build that stops before the tests must not
 *   turn a broken update green. The base is verified once per [RunBase].
 * - Failing tests are rerun once. A test that passes on the rerun is flaky.
 * - Tests that still fail run on the [base] commit, and only those. Failing there too means pre-existing, passing there
 *   means caused by the update. Without a base, or if the base doesn't build, they are unattributed.
 *
 * Each build and test run may take the time [timeLeft] returns when it starts.
 */
fun attribute(
    adapter: EcosystemAdapter,
    repo: Path,
    base: BaseCommit?,
    timeLeft: () -> Duration = { Duration.INFINITE },
): Verification {
    val (tests, problems) = adapter.verify(repo, timeLeft()).partition { it.test != null }
    // Read before the reruns, which run only some tests.
    val testsRun = adapter.testsRun(repo)
    // A build that stopped before the tests may stop there on the base too: its build error stays the update's.
    val testsRan = (testsRun ?: 0) > 0
    val onBase =
        if (base != null && testsRan && problems.any(::isBuildError)) {
            (base as? RunBase ?: RunBase(base)).buildErrors(adapter, timeLeft())
        } else {
            emptySet()
        }
    val (preExistingBuild, buildProblems) =
        if (onBase.isEmpty()) {
            emptyList<Diagnostic>() to problems
        } else {
            problems.partition {
                isBuildError(
                    it,
                ) && pathless(it, repo) in onBase
            }
        }
    val stillFailing =
        if (tests.isEmpty()) {
            tests
        } else {
            val failedAgain = failedTests(adapter.runTests(repo, tests, timeLeft()), "rerun")
            tests.filter { failedAgain == null || it.test in failedAgain }
        }
    val flaky = tests - stillFailing.toSet()
    val failedOnBase =
        if (stillFailing.isEmpty() || base == null) {
            null
        } else {
            failedTests(base.checkout { adapter.runTests(it, stillFailing, timeLeft()) }, "base commit")
        }
    if (failedOnBase == null) {
        return Verification(
            causedByUpdate = buildProblems,
            preExisting = preExistingBuild,
            flaky = flaky,
            unattributed = stillFailing,
            testsRun = testsRun,
        )
    }
    val (preExisting, broken) = stillFailing.partition { it.test in failedOnBase }
    return Verification(
        causedByUpdate = buildProblems + broken,
        preExisting = preExistingBuild + preExisting,
        flaky = flaky,
        testsRun = testsRun,
    )
}

private fun isBuildError(diagnostic: Diagnostic) = diagnostic.file == null && diagnostic.test == null

// What a build error's message keeps of the checkout it ran in.
private const val CHECKOUT = "<checkout>"

/**
 * [diagnostic] as it compares with the same build error of another checkout: [checkout]'s path replaced in its message,
 * as given and as its real path (on macOS a temporary directory is also under `/private`), the longer first.
 */
private fun pathless(
    diagnostic: Diagnostic,
    checkout: Path,
): ErrorKey {
    val paths = listOf(checkout.toString(), checkout.toAbsolutePath().toString(), checkout.toRealPath().toString())
    val message = paths.distinct().sortedByDescending { it.length }.fold(diagnostic.message) { text, path -> text.replace(path, CHECKOUT) }
    return diagnostic.copy(message = message).key
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
