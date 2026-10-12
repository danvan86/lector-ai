package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.toPath

/**
 * Turns Gradle's plain console output into [Diagnostic]s: compile errors (javac, kotlinc) and build failures without a
 * location (the `* What went wrong:` blocks). Test failures come from the reports instead, see [GRADLE_REPORTS].
 */
internal object GradleConsole {
    // javac as Gradle prints it: /path/App.java:7: error: cannot find symbol
    private val javacError = Regex("""^(/.+\.java):(\d+): error: (.*)$""")

    // kotlinc, Kotlin 1.9 and 2.x alike: e: file:///path/App.kt:4:56 Unresolved reference 'x'.
    private val kotlincError = Regex("""^e: (file:/.+\.kts?):(\d+):(\d+):? (.*)$""")

    private const val WHAT_WENT_WRONG = "* What went wrong:"

    // What ends a failure's explanation: the next section, the separator between failures, or the build result.
    private val failureEnd = Regex("""^(\* \w.*|=+|BUILD FAILED.*)$""")

    // Failures that only summarize what the compile errors and the test reports already say.
    private val compileSummary = Regex("""[Cc]ompilation (failed|error)|Script compilation error""")
    private const val TEST_SUMMARY = "There were failing tests"

    // Gradle 8.0–8.7 and 8.8 and newer: no installed JDK matches a toolchain request.
    private val missingToolchain =
        Regex("""(?:No matching toolchains found|Cannot find a Java installation on your machine).*\{languageVersion=(\d+)""")

    fun parse(
        output: String,
        repo: Path,
    ): List<Diagnostic> {
        val lines = output.lines()
        val compileErrors = javacErrors(lines, repo) + kotlincErrors(lines, repo)
        val failures =
            failures(
                lines,
            ).filterNot { TEST_SUMMARY in it || (compileErrors.isNotEmpty() && compileSummary.containsMatchIn(it)) }
        return (compileErrors + failures.map(::buildError)).distinct()
    }

    /** The Java version of a toolchain request that no installed JDK matches, or null. */
    fun missingToolchain(output: String): Int? = missingToolchain.find(output)?.let { it.groupValues[1].toInt() }

    /**
     * javac errors. Next come the source line and a caret that gives the column, then the indented details (`symbol:`,
     * `location:`), which become part of the message.
     */
    private fun javacErrors(
        lines: List<String>,
        repo: Path,
    ): List<Diagnostic> =
        lines.indices.mapNotNull { start ->
            javacError.matchEntire(lines[start])?.let { match ->
                val (file, line, message) = match.destructured
                val caret = lines.getOrNull(start + 2)?.takeIf { it.trim() == "^" }
                val details = lines.drop(start + if (caret == null) 1 else 3).takeWhile { it.startsWith(" ") }.map { it.trim() }
                val column = caret?.let { it.indexOf('^') + 1 }
                Diagnostic(
                    relativeTo(repo, Path.of(file)),
                    line.toInt(),
                    column,
                    null,
                    (listOf(message) + details).joinToString("\n"),
                    Severity.ERROR,
                    "javac",
                )
            }
        }

    private fun kotlincErrors(
        lines: List<String>,
        repo: Path,
    ): List<Diagnostic> =
        lines.mapNotNull { kotlincError.matchEntire(it) }.map { match ->
            val (file, line, column) = match.destructured
            val message = match.groupValues.last()
            Diagnostic(relativeTo(repo, URI(file).toPath()), line.toInt(), column.toInt(), null, message, Severity.ERROR, "kotlinc")
        }

    /** The text of each `* What went wrong:` block, with the `* Where:` line before it if there is one. */
    private fun failures(lines: List<String>): List<String> =
        lines.indices.filter { lines[it] == WHAT_WENT_WRONG }.map { start ->
            val before = lines.take(start).dropLastWhile { it.isBlank() }
            val where = if (before.getOrNull(before.size - 2) == "* Where:") before.last() else null
            val text =
                lines
                    .drop(start + 1)
                    .takeWhile { !failureEnd.matches(it) }
                    .joinToString("\n")
                    .trim()
            if (where == null) text else "$where\n$text"
        }

    private fun buildError(message: String) = Diagnostic(null, null, null, null, message, Severity.ERROR, "gradle")
}
