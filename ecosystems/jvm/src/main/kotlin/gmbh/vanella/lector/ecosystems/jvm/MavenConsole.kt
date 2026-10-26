package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.toPath

/**
 * Turns Maven's console output into [Diagnostic]s: compile errors (javac, kotlinc) and build errors without a location
 * (failed goals, unreadable POMs). Test failures come from the reports instead, see [TestReports].
 */
internal object MavenConsole {
    private const val ERROR = "[ERROR] "
    private const val GOAL_FAILED = ERROR + "Failed to execute goal"

    // javac, and the summary of Kotlin 1.x: /path/App.java:[8,9] message
    private val bracketLocation = Regex("""^(/.+\.(?:java|kt)):\[(\d+),(\d+)] (.*)$""")

    // Kotlin 1.x: /path/App.kt: (4, 56) message
    private val kotlin1Location = Regex("""^(/.+\.kt): \((\d+), (\d+)\) (.*)$""")

    // Kotlin 2.x: file:///path/App.kt:4:56 message
    private val kotlin2Location = Regex("""^(file:/.+\.kt):(\d+):(\d+) (.*)$""")

    private val projectWithErrors = Regex("""^ {2}The project \S+ \(.+\) has \d+ errors?$""")
    private val helpReference = Regex(""" -> \[Help \d+]$""")

    // A module of the reactor summary that never ran because a module it depends on failed: `[INFO] b ..... SKIPPED`
    private val skippedModule = Regex("""^\[INFO] \S.* \.+ SKIPPED$""", RegexOption.MULTILINE)

    /** Whether the reactor summary in [output] names a module that Maven skipped after another failed. */
    fun skippedModule(output: String) = skippedModule.containsMatchIn(output)

    fun parse(
        output: String,
        repo: Path,
    ): List<Diagnostic> {
        val lines = output.lines()
        return (compileErrors(lines, repo) + goalFailures(lines) + projectErrors(lines)).distinct()
    }

    /** Compile errors. Indented lines that follow belong to the message (javac's `symbol:` and `location:`). */
    private fun compileErrors(
        lines: List<String>,
        repo: Path,
    ): List<Diagnostic> {
        val errors = mutableListOf<Diagnostic>()
        var current: Diagnostic? = null
        for (line in lines) {
            val text = line.removePrefix(ERROR)
            val error = if (line.startsWith(ERROR)) compileError(text, repo) else null
            when {
                error != null -> {
                    current = error.also { errors += it }
                }

                current != null && text.isNotBlank() && text.first().isWhitespace() -> {
                    val extended = current.copy(message = current.message + "\n" + text.trim())
                    errors[errors.lastIndex] = extended
                    current = extended
                }

                else -> {
                    current = null
                }
            }
        }
        return errors
    }

    private fun compileError(
        text: String,
        repo: Path,
    ): Diagnostic? {
        val bracket = bracketLocation.matchEntire(text) ?: kotlin1Location.matchEntire(text)
        val match = bracket ?: kotlin2Location.matchEntire(text) ?: return null
        val (file, line, column) = match.destructured
        val message = match.groupValues.last()
        val path = if (bracket != null) Path.of(file) else URI(file).toPath()
        val tool = if (file.endsWith(".kt")) "kotlinc" else "javac"
        return Diagnostic(relativeTo(repo, path), line.toInt(), column.toInt(), null, message, Severity.ERROR, tool)
    }

    /**
     * `Failed to execute goal …` with the lines that explain it. Summaries of compile errors and of test failures are
     * skipped: the compile errors and the test reports already cover them.
     */
    private fun goalFailures(lines: List<String>): List<Diagnostic> =
        lines.indices
            .filter { lines[it].startsWith(GOAL_FAILED) }
            .map { start ->
                val details = lines.drop(start + 1).takeWhile(::isGoalFailureDetail)
                (listOf(lines[start]) + details).map { it.removePrefix(ERROR) }
            }.filterNot { failure ->
                failure.first().endsWith("There are test failures.") ||
                    failure.drop(1).any { bracketLocation.matches(it) || kotlin2Location.matches(it) }
            }.map { buildError(it.joinToString("\n")) }

    /** Problems in POMs that keep Maven from building at all, listed under `The project … has N errors`. */
    private fun projectErrors(lines: List<String>): List<Diagnostic> =
        lines.indices
            .filter { lines[it].startsWith(ERROR) && projectWithErrors.matches(lines[it].removePrefix(ERROR)) }
            .flatMap { start ->
                lines
                    .drop(start + 1)
                    .takeWhile { it.startsWith("$ERROR    ") }
                    .map { buildError(it.removePrefix(ERROR).trim().replace(helpReference, "")) }
            }

    private fun buildError(message: String) = Diagnostic(null, null, null, null, message, Severity.ERROR, "maven")

    // The explanation ends at Maven's help reference, at an empty line, or at the next failure.
    private fun isGoalFailureDetail(line: String) =
        line.startsWith(ERROR) &&
            line.removePrefix(ERROR).isNotBlank() &&
            !line.startsWith("$ERROR-> [Help") &&
            !line.startsWith(GOAL_FAILED)
}

/** [path] relative to [repo] with `/` as separator, or as it is if it lies outside. */
internal fun relativeTo(
    repo: Path,
    path: Path,
): String = if (path.startsWith(repo)) repo.relativize(path).invariantSeparatorsPathString else path.invariantSeparatorsPathString
