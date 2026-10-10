package gmbh.vanella.lector.core

/**
 * One problem reported by a compiler, a test run, or the build itself, the same for every ecosystem.
 *
 * - [file], [line], and [column] are null for failures without a source location (dependency resolution, plugin errors).
 *   [file] is relative to the repository root, with `/` as separator.
 * - [code] is the tool's own identifier for the problem, if it reports one (for a test failure: the exception type).
 * - [test] names the failing test (`com.example.FooTest#bar`) for test failures, and is null for everything else.
 *   Attribution reruns tests by this name.
 *
 * Whether the update caused a problem is not a property of the problem. It is the result of comparing runs, see [Verification].
 */
data class Diagnostic(
    val file: String?,
    val line: Int?,
    val column: Int?,
    val code: String?,
    val message: String,
    val severity: Severity,
    val tool: String,
    val test: String? = null,
)

/** Only errors are collected. Warnings would need their own attribution, or every old warning would fail the job. */
enum class Severity {
    ERROR,
}
