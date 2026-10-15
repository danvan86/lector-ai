package gmbh.vanella.lector.benchmark

import com.github.ajalt.clikt.testing.test
import gmbh.vanella.lector.core.Edit
import gmbh.vanella.lector.core.Proposal
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class BenchmarkCommandTest {
    @TempDir
    lateinit var dir: Path

    @TempDir
    lateinit var origin: Path

    private lateinit var corpus: Corpus
    private val harnesses = mutableListOf<List<Any?>>()

    @BeforeTest
    fun `create a corpus with one case`() {
        git(origin, "init", "--quiet")
        git(origin, "config", "uploadpack.allowAnySHA1InWant", "true")
        origin.resolve("App.java").writeText("class App {\n    void run() {\n        old(1);\n    }\n}\n")
        origin.resolve("pom.xml").writeText("$LIB 1.0\n")
        git(origin, "add", ".")
        git(origin, "commit", "--quiet", "-m", "base")
        val base = git(origin, "rev-parse", "HEAD").trim()
        origin.resolve("pom.xml").writeText("$LIB 2.0\n")
        git(origin, "commit", "--quiet", "-am", "bump")
        val head = git(origin, "rev-parse", "HEAD").trim()
        corpus = Corpus(listOf(Case("lib-2", SYNTHETIC, origin.toString(), base, head, listOf(Change(LIB, "1.0", "2.0")), null, MAVEN)))
    }

    private fun command() =
        benchmarkCommand({ corpus }) { url, model, toolCommit, settings ->
            harnesses += listOf(url, model, toolCommit, settings)
            val llm = ScriptedLlm({ Proposal.Edits(listOf(Edit("App.java", "old(1);", "fixed(1);")), "Fix it") })
            Harness(listOf(FakeAdapter()), FakeIndex, llm, GitCheckout, ToolUnderTest(toolCommit, model))
        }

    @Test
    fun `matrix prints one job per case, mode, and repetition`() {
        val result = command().test("matrix")

        assertEquals(0, result.statusCode, result.stderr)
        val expected =
            plan(
                corpus,
            ).joinToString(",", """{"include":[""", "]}") { """{"case":"lib-2","mode":"${it.mode.id}","repetition":${it.repetition}}""" }
        assertEquals("$expected\n", result.stdout)
    }

    @Test
    fun `matrix can be limited to some cases, and fails on an unknown one`() {
        assertEquals(0, command().test("matrix --cases lib-2, ").statusCode)
        val unknown = command().test("matrix --cases nope")

        assertEquals(2, unknown.statusCode)
        assertContains(unknown.stderr, "No case nope in the corpus")
    }

    @Test
    fun `run runs one case in one mode and writes its result`() {
        val output = dir.resolve("runs/lib-2-recipe-and-fix-loop-2.json")
        val work = dir.resolve("work").createDirectories()

        val result =
            command().test(
                "run --case lib-2 --mode recipe-and-fix-loop --repetition 2 --output $output --tool-commit abc --model qwen2.5-coder:7b " +
                    "--ollama-url http://ollama:11434 --work-dir $work",
            )

        assertEquals(0, result.statusCode, result.stderr)
        assertEquals("lib-2 recipe-and-fix-loop #2: valid\n", result.stdout)
        val run = Results.decodeRun(output.readText())
        assertEquals(RunKey("lib-2", Mode.RECIPE_AND_FIX_LOOP, 2), run.key)
        assertEquals(true, run.metrics?.green)
        assertEquals(listOf<List<Any?>>(listOf(URI("http://ollama:11434"), "qwen2.5-coder:7b", "abc", null)), harnesses)
    }

    @Test
    fun `run defaults to the local Ollama and a new working directory, and names why a run is not valid`() {
        corpus = corpus.copy(cases = corpus.cases.map { it.copy(head = it.base) })
        val output = dir.resolve("run.json")

        val result = command().test("run --case lib-2 --mode recipe-only --repetition 1 --output $output --tool-commit abc --model m")

        assertEquals("lib-2 recipe-only #1: invalid, the head commit has no failure caused by the update\n", result.stdout)
        assertEquals(URI("http://localhost:11434"), harnesses.single().first())
    }

    @Test
    fun `run rejects a repetition the mode doesn't have`() {
        val result =
            command().test(
                "run --case lib-2 --mode recipe-only --repetition 2 --output ${dir.resolve("r.json")} --tool-commit a --model m",
            )

        assertEquals(2, result.statusCode)
        assertContains(result.stderr, "Mode recipe-only runs 1 time(s), not 2")
    }

    @Test
    fun `merge collects the runs into results json, with the missing ones`() {
        val runs = dir.resolve("runs/nested").createDirectories()
        val key = RunKey("lib-2", Mode.RECIPE_ONLY, 1)
        runs
            .resolve(
                "one.json",
            ).writeText(Results.encodeRun(RunResult(key.case, key.mode, 1, "abc", "m", RunStatus.VALID, null, metrics(true), MAVEN)))
        runs.resolve("notes.txt").writeText("not a run")
        val output = dir.resolve("results/2026-10-12/results.json")

        val result = command().test("merge --runs ${dir.resolve("runs")} --output $output --runner ubuntu-24.04 --date 2026-10-12")

        assertEquals(0, result.statusCode, result.stderr)
        assertEquals("7 runs in $output\n", result.stdout)
        val results = Results.decode(output.readText())
        assertEquals("ubuntu-24.04", results.runner)
        assertEquals(6, results.runs.count { it.status == RunStatus.MISSING })
    }

    @Test
    fun `merge without any run result says so, with the directory`() {
        val runs = dir.resolve("runs").createDirectories()

        val result = command().test("merge --runs $runs --output ${dir.resolve("r.json")} --runner r --date 2026-10-12")

        assertEquals(2, result.statusCode)
        assertEquals("", result.stdout)
        assertEquals("Error: No run results under $runs\n", result.stderr)
    }

    @Test
    fun `merge rejects a date that isn't one`() {
        val result = command().test("merge --runs $dir --output ${dir.resolve("r.json")} --runner r --date 12.10.2026")

        assertEquals(1, result.statusCode)
        assertContains(result.stderr, "not a date like 2026-10-12: 12.10.2026")
    }

    @Test
    fun `report writes report md next to results json, with the review if there is one`() {
        dir.resolve("results.json").writeText(fixture("results.json"))
        dir.resolve("review.json").writeText(fixture("review.json"))

        val result = command().test("report $dir")

        assertEquals(0, result.statusCode, result.stderr)
        assertEquals("Wrote ${dir.resolve("report.md")}\n", result.stdout)
        assertEquals(fixture("report.md"), dir.resolve("report.md").readText())
    }

    @Test
    fun `report without a review says it's missing, and without results fails`() {
        val empty = dir.resolve("empty").createDirectories()
        dir.resolve("results.json").writeText(fixture("results.json"))

        assertEquals(0, command().test("report $dir").statusCode)
        assertContains(dir.resolve("report.md").readText(), "Not done yet")
        val missing = command().test("report $empty")
        assertEquals(2, missing.statusCode)
        assertContains(missing.stderr, "results.json is missing")
    }

    @Test
    fun `the real harness loads the recipe index, the Maven adapter, and the TypeScript adapter`() {
        // Fails if the bundled recipe index or the Maven adapter can't be loaded.
        realHarness(URI("http://localhost:11434"), "m", "abc", null)
        realHarness(URI("http://localhost:11434"), "m", "abc", null, mapOf("LECTOR_JEST_JUNIT" to "/opt/jest-junit"))
    }

    @Test
    fun `help lists the commands, with the shipped corpus and the real harness`() {
        val result = benchmarkCommand().test("--help")

        assertEquals(0, result.statusCode)
        listOf("matrix", "run", "merge", "report").forEach { assertContains(result.stdout, it) }
        val matrix = benchmarkCommand().test("matrix").stdout
        assertEquals(
            Corpus
                .load()
                .cases
                .sumOf {
                    if (it.ecosystem ==
                        MAVEN
                    ) {
                        7L
                    } else {
                        3L
                    }
                }.toInt(),
            Regex("\"repetition\"").findAll(matrix).count(),
        )
    }
}
