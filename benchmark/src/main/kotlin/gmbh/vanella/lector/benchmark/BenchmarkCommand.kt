package gmbh.vanella.lector.benchmark

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import gmbh.vanella.lector.ecosystems.jvm.MavenAdapter
import gmbh.vanella.lector.ecosystems.jvm.installedJdks
import gmbh.vanella.lector.ecosystems.typescript.TypeScriptAdapter
import gmbh.vanella.lector.ecosystems.typescript.installedNodes
import gmbh.vanella.lector.llm.OllamaClient
import gmbh.vanella.lector.recipes.BundledRecipeIndex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

/** Builds the harness for one run: the real adapters, the bundled recipe index, and Ollama. */
typealias HarnessFactory = (ollamaUrl: URI, model: String, toolCommit: String, mavenSettings: Path?) -> Harness

/** The benchmark's commands, for the workflow (spec section 6). Not a command of the CLI the product ships. */
internal fun benchmarkCommand(
    corpus: () -> Corpus = Corpus::load,
    harness: HarnessFactory = ::realHarness,
) = NoOpCliktCommand(name = "benchmark").subcommands(
    MatrixCommand(corpus),
    RunCommand(corpus, harness),
    MergeCommand(corpus),
    ReportCommand(),
)

internal fun realHarness(
    ollamaUrl: URI,
    model: String,
    toolCommit: String,
    mavenSettings: Path?,
) = realHarness(ollamaUrl, model, toolCommit, mavenSettings, System.getenv())

/** The harness of the product's adapters, configured from [environment] as the CLI configures them. */
internal fun realHarness(
    ollamaUrl: URI,
    model: String,
    toolCommit: String,
    mavenSettings: Path?,
    environment: Map<String, String>,
) = Harness(
    adapters =
        listOf(
            MavenAdapter(mavenSettings, jdks = installedJdks(environment)),
            // The workflow installs Node and jest-junit as the tool image has them (LECTOR_NODE_<major>, LECTOR_JEST_JUNIT).
            TypeScriptAdapter(
                nodes = installedNodes(environment),
                environment = environment,
                jestJunit = environment["LECTOR_JEST_JUNIT"]?.let { Path.of(it) },
            ),
        ),
    index = BundledRecipeIndex.load(),
    llm = OllamaClient(ollamaUrl, model),
    checkout = GitCheckout,
    tool = ToolUnderTest(toolCommit, model),
)

/**
 * A benchmark command. An invalid input (an unknown case, a malformed file) exits with `2` and its message, like any
 * failure of the tool.
 */
private abstract class BenchmarkStep(
    name: String,
) : CliktCommand(name) {
    protected abstract fun execute()

    final override fun run() {
        try {
            execute()
        } catch (e: IllegalArgumentException) {
            throw CliktError("Error: ${e.message}", e, statusCode = 2)
        }
    }
}

/** `--cases a,b`: only these cases. */
private fun CliktCommand.casesOption() =
    option("--cases", metavar = "<id,…>", help = "Only these cases, comma-separated (default: all)")
        .convert { text ->
            text
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }

/** `matrix`: the GitHub Actions matrix, one job per case, mode, and repetition. */
private class MatrixCommand(
    private val corpus: () -> Corpus,
) : BenchmarkStep("matrix") {
    private val cases by casesOption()

    override fun help(context: Context) = "Prints the workflow's job matrix as JSON."

    override fun execute() {
        val include =
            plan(corpus(), cases).map {
                JsonObject(
                    mapOf(
                        "case" to JsonPrimitive(it.case),
                        "mode" to JsonPrimitive(it.mode.id),
                        "repetition" to JsonPrimitive(it.repetition),
                    ),
                )
            }
        echo(JsonObject(mapOf("include" to JsonArray(include))).toString())
    }
}

/** `run`: one run of the harness, written as JSON. */
private class RunCommand(
    private val corpus: () -> Corpus,
    private val harness: HarnessFactory,
) : BenchmarkStep("run") {
    private val case by option("--case", metavar = "<id>", help = "The case").required()
    private val mode by option("--mode", help = "The mode").choice(Mode.entries.associateBy { it.id }).required()
    private val repetition by option("--repetition", metavar = "<n>", help = "The repetition, from 1").int().restrictTo(min = 1).required()
    private val output by option("--output", metavar = "<file>", help = "Where the run's result goes").path().required()
    private val toolCommit by option("--tool-commit", metavar = "<sha>", help = "The commit of lector-ai that runs").required()
    private val model by option("--model", metavar = "<name>", help = "The Ollama model").required()
    private val ollamaUrl by option("--ollama-url", metavar = "<url>", help = "The Ollama server")
        .convert { URI(it) }
        .default(URI("http://localhost:11434"), defaultForHelp = "http://localhost:11434")
    private val workDir by option(
        "--work-dir",
        metavar = "<dir>",
        help = "Where the case is checked out (default: a new temporary directory)",
    ).path(mustExist = true, canBeFile = false)
    private val mavenSettings by option(
        "--maven-settings",
        metavar = "<file>",
        help = "Maven settings file",
    ).path(mustExist = true, canBeDir = false)

    override fun help(context: Context) = "Runs one case in one mode and writes the result."

    override fun execute() {
        val case = corpus().case(case)
        require(repetition <= mode.repetitions) { "Mode ${mode.id} runs ${mode.repetitions} time(s), not $repetition" }
        val dir = workDir ?: Files.createTempDirectory("lector-benchmark")
        val result = harness(ollamaUrl, model, toolCommit, mavenSettings).run(case, mode, repetition, dir)
        output.toAbsolutePath().parent.createDirectories()
        output.writeText(Results.encodeRun(result))
        echo("${case.id} ${mode.id} #$repetition: ${result.status.name.lowercase()}${result.reason?.let { ", $it" }.orEmpty()}")
    }
}

/** `merge`: the runs of one benchmark run into `results.json`. */
private class MergeCommand(
    private val corpus: () -> Corpus,
) : BenchmarkStep("merge") {
    private val runs by option("--runs", metavar = "<dir>", help = "The directory with the runs' JSON files, searched recursively")
        .path(mustExist = true, canBeFile = false)
        .required()
    private val output by option("--output", metavar = "<file>", help = "Where results.json goes").path().required()
    private val runner by option(
        "--runner",
        metavar = "<text>",
        help = "The runner type, e.g. GitHub-hosted ubuntu-24.04, 4 CPUs",
    ).required()
    private val date by option("--date", metavar = "<yyyy-mm-dd>", help = "The date of the benchmark run")
        .convert { text ->
            try {
                LocalDate.parse(text).toString()
            } catch (_: DateTimeParseException) {
                fail("not a date like 2026-10-12: $text")
            }
        }.required()
    private val cases by casesOption()

    override fun help(context: Context) = "Merges the runs' results into results.json."

    override fun execute() {
        val results =
            runs
                .walk()
                .filter { it.extension == "json" }
                .sorted()
                .map { file -> Results.decodeRun(file.readText()) }
                .toList()
        // All jobs can fail before they write a result, e.g. if the Ollama image wasn't built.
        require(results.isNotEmpty()) { "No run results under $runs" }
        val merged = Results.merge(plan(corpus(), cases), results, runner, date, corpus().cases.associate { it.id to it.ecosystem })
        output.toAbsolutePath().parent.createDirectories()
        output.writeText(merged.encode())
        echo("${merged.runs.size} runs in $output")
    }
}

/** `report`: `report.md` from `results.json` and, if it is there, `review.json`, all in one directory. */
private class ReportCommand : BenchmarkStep("report") {
    private val dir by argument(help = "The directory with results.json").path(mustExist = true, canBeFile = false)

    override fun help(context: Context) = "Generates report.md from results.json and review.json."

    override fun execute() {
        val resultsFile = dir.resolve("results.json")
        require(resultsFile.isRegularFile()) { "$resultsFile is missing" }
        val reviewFile = dir.resolve("review.json")
        val review = if (reviewFile.exists()) Review.decode(reviewFile.readText()) else null
        val report = dir.resolve("report.md")
        report.writeText(Report.render(Results.decode(resultsFile.readText()), review))
        echo("Wrote $report")
    }
}
