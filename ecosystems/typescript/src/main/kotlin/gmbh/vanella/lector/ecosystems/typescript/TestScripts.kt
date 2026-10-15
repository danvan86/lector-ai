package gmbh.vanella.lector.ecosystems.typescript

/** The test runners whose failures the adapter reads per test, from a JUnit report. */
internal enum class Runner(
    val bin: String,
) {
    JEST("jest"),
    VITEST("vitest"),
}

/** How a package's `test` script calls its runner: the variables it sets, and the arguments it passes. */
internal data class TestCommand(
    val runner: Runner,
    val environment: Map<String, String>,
    val arguments: List<String>,
)

private val ASSIGNMENT = Regex("""^([A-Za-z_][A-Za-z0-9_]*)=(.*)$""")
private val SEPARATORS = setOf("&&", "||", ";", "|")

// What runs a package's binary: `npx jest`, `pnpm exec jest`, `yarn jest`, `npm exec -- jest`, `cross-env A=1 jest`.
private val LAUNCHERS = setOf("npx", "pnpm", "yarn", "npm", "exec", "run", "--", "cross-env")

/**
 * Finds the runner in a `test` script (spec section 4): the first command of the script whose program is `jest` or
 * `vitest`, also behind a launcher, with its variables and arguments. The script is only read, never run: the adapter
 * calls the runner itself, with its reporter. Anything else is no runner the adapter knows.
 */
internal object TestScripts {
    fun command(script: String): TestCommand? =
        segments(tokens(script)).firstNotNullOfOrNull { segment ->
            val environment = mutableMapOf<String, String>()
            val rest =
                segment.dropWhile { token ->
                    val assignment = ASSIGNMENT.matchEntire(token)
                    if (assignment != null) environment[assignment.groupValues[1]] = assignment.groupValues[2]
                    assignment != null || token in LAUNCHERS || token.startsWith("--no")
                }
            val runner = Runner.entries.firstOrNull { it.bin == rest.firstOrNull() }
            if (runner == null) null else TestCommand(runner, environment, arguments(runner, rest.drop(1)))
        }

    /** Vitest always runs once here: the adapter adds `run` itself. */
    private fun arguments(
        runner: Runner,
        arguments: List<String>,
    ) = when (runner) {
        Runner.VITEST -> arguments.filterIndexed { index, argument -> !(index == 0 && argument == "run") && argument != "--run" }
        Runner.JEST -> arguments
    }

    private fun segments(tokens: List<String>): List<List<String>> {
        val segments = mutableListOf(mutableListOf<String>())
        for (token in tokens) {
            if (token in SEPARATORS) segments += mutableListOf<String>() else segments.last() += token
        }
        return segments
    }

    /** The words of [script] as a shell splits them, with single and double quotes. */
    private fun tokens(script: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var inToken = false
        for (char in script) {
            when {
                quote != null && char == quote -> {
                    quote = null
                }

                quote != null -> {
                    current.append(char)
                }

                char == '"' || char == '\'' -> {
                    quote = char
                    inToken = true
                }

                char.isWhitespace() && inToken -> {
                    tokens += current.toString()
                    current.clear()
                    inToken = false
                }

                !char.isWhitespace() -> {
                    current.append(char)
                    inToken = true
                }
            }
        }
        if (inToken) tokens += current.toString()
        return tokens
    }
}
