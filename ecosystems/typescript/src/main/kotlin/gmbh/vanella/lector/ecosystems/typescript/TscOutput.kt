package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

// `src/a.ts(4,7): error TS2322: Type 'string' is not assignable to type 'number'.` A pnpm install prefixes a workspace
// package's lifecycle script with its directory and the script: `packages/lib prepare: src/a.ts(4,7): error …`.
private val LOCATED = Regex("""^(?:(\S+) [\w:.-]+: )?(.+)\((\d+),(\d+)\): error (TS\d+): (.*)$""")

// The regex group of a located error's column; its code and message follow.
private const val COLUMN = 4

// `error TS18003: No inputs were found in config file '…'.`
private val GLOBAL = Regex("""^error (TS\d+): (.*)$""")

/**
 * The errors in the output of `tsc --pretty false`, the same in TypeScript 5 to 7. An error's further lines, indented,
 * belong to its message. Lines of anything else, like the package manager's, are ignored.
 */
internal object TscOutput {
    /** The errors of a `tsc` that ran in [workDir], with paths relative to the repository at [root]. */
    fun parse(
        output: String,
        root: Path,
        workDir: Path,
    ): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        for (line in output.lines()) {
            val located = LOCATED.matchEntire(line)
            val global = GLOBAL.matchEntire(line)
            val last = diagnostics.lastOrNull()
            when {
                located != null -> {
                    val (pkg, file, row) = located.destructured
                    val (column, code, message) = located.groupValues.drop(COLUMN)
                    val dir = if (pkg.isEmpty()) workDir else root.resolve(pkg)
                    diagnostics += Diagnostic(relative(root, dir, file), row.toInt(), column.toInt(), code, message, Severity.ERROR, "tsc")
                }

                global != null -> {
                    diagnostics += Diagnostic(null, null, null, global.groupValues[1], global.groupValues[2], Severity.ERROR, "tsc")
                }

                last != null && line.startsWith(" ") && line.isNotBlank() -> {
                    diagnostics[diagnostics.lastIndex] = last.copy(message = last.message + "\n" + line.trimEnd())
                }
            }
        }
        return diagnostics
    }

    private fun relative(
        root: Path,
        workDir: Path,
        file: String,
    ) = root.relativize(workDir.resolve(file).normalize()).invariantSeparatorsPathString
}
