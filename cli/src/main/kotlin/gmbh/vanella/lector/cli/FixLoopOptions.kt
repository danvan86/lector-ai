package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import gmbh.vanella.lector.core.Budgets
import java.net.URI
import java.net.URISyntaxException
import kotlin.time.Duration.Companion.minutes

private val DEFAULTS = Budgets()
private const val DEFAULT_OLLAMA_URL = "http://localhost:11434"

/** The model the benchmark measures (M6), and the one the real-model test runs by default. */
internal const val DEFAULT_MODEL = "qwen2.5-coder:7b"

/** The options of the fix loop, shared by `heal` and `pr`: the Ollama server, the model, and the budgets. */
internal class FixLoopOptions : OptionGroup() {
    val ollamaUrl by option("--ollama-url", metavar = "<url>", help = "The Ollama server")
        .httpUrl()
        .default(URI(DEFAULT_OLLAMA_URL), defaultForHelp = DEFAULT_OLLAMA_URL)
    val model by option("--model", metavar = "<name>", help = "The Ollama model").default(DEFAULT_MODEL)
    private val attemptsPerCluster by budget("--max-attempts-per-cluster", "Attempts per error cluster", DEFAULTS.attemptsPerCluster)
    private val attempts by budget("--max-attempts", "Attempts in total", DEFAULTS.attempts)
    private val timeLimit by budget("--time-limit", "Minutes before the loop stops", DEFAULTS.time.inWholeMinutes.toInt(), "<minutes>")
    private val attemptsWithoutProgress by budget(
        "--max-attempts-without-progress",
        "Attempts in a row without progress before the loop stops",
        DEFAULTS.attemptsWithoutProgress,
    )
    private val maxChangedLines by budget(
        "--max-changed-lines",
        "Lines (added plus removed) one attempt may change before the guardrails reject it",
        DEFAULTS.changedLinesPerAttempt,
    )

    fun budgets() = Budgets(attemptsPerCluster, attempts, timeLimit.minutes, attemptsWithoutProgress, maxChangedLines)

    private fun budget(
        name: String,
        help: String,
        default: Int,
        metavar: String = "<n>",
    ) = option(name, metavar = metavar, help = help).int().restrictTo(min = 1).default(default)
}

/** An http or https URL with a host. Anything else fails when the command line is parsed, before anything runs. */
internal fun NullableOption<String, String>.httpUrl() =
    convert { text ->
        val url =
            try {
                URI(text)
            } catch (e: URISyntaxException) {
                fail("not a URL: ${e.message}")
            }
        if (url.scheme !in listOf("http", "https") || url.host == null) fail("not an http or https URL with a host: $text")
        url
    }
