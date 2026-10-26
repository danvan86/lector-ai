package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Edit
import gmbh.vanella.lector.core.LlmClient
import gmbh.vanella.lector.core.Recipe
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.RecipeMatch
import gmbh.vanella.lector.core.RecipeRun
import gmbh.vanella.lector.core.RecipeSelection
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.runProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class HealCommandTest {
    @TempDir
    lateinit var repo: Path

    private val upgrade = Recipe("org.example.UpgradeSpringBoot_3_4", "org.example:recipes:1.0", "Apache License 2.0")
    private val index = RecipeIndex { _, _ -> RecipeSelection(RecipeMatch(upgrade, "3.4.x", 1), emptyList()) }
    private val noRecipe = RecipeIndex { _, _ -> RecipeSelection.NONE }
    private val fix = Edit("App.java", "broken();", "fixed();")
    private val clients = mutableListOf<Pair<URI, String>>()
    private val args = "--package $PARENT --from 3.3.5 --to 3.4.1"

    @BeforeTest
    fun `create a clean repository whose code the update broke`() {
        git("init", "--quiet")
        repo.resolve("pom.xml").writeText("<parent>3.4.1</parent>\n")
        repo.resolve("App.java").writeText("class App {\n    void run() {\n        broken();\n    }\n}\n")
        git("add", ".")
        git("commit", "--quiet", "-m", "head")
    }

    @Test
    fun `commits the recipe's result, then fixes the rest, one commit each, and exits with 0`() {
        val llm = ScriptedLlm(fix)

        val result = command(llm = llm).test("$repo $args")

        assertEquals(0, result.statusCode, result.stderr)
        assertEquals("", result.stderr)
        val subjects = git("log", "--format=%an|%s").trim().lines()
        assertEquals(
            listOf("lector|Call fixed() instead of broken()", "lector|Apply recipe org.example.UpgradeSpringBoot_3_4", "Lector|head"),
            subjects,
        )
        assertEquals("", git("status", "--porcelain"))
        val recipeDiff = llm.requests.single().recipeDiff
        assertContains(recipeDiff.orEmpty(), "-<parent>3.4.1</parent>\n+<parent>3.4.5</parent>")
    }

    @Test
    fun `the text report has the recipe, the fix loop, and the final verification`() {
        val result = command(llm = ScriptedLlm(Edit("App.java", "nowhere", "x"), fix)).test("$repo $args")

        val recipeCommit = git("rev-parse", "--short=7", "HEAD~1").trim()
        val fixCommit = git("rev-parse", "--short=7", "HEAD").trim()
        val stdout = result.stdout
        assertContains(stdout, "Recipe (applied)\n")
        assertContains(stdout, "  Commit: $recipeCommit\n")
        assertContains(
            stdout,
            """
            Fix loop (2 attempts, 1 accepted; stopped: nothing caused by the update is left)
              Attempts (2):
                1. symbol:broken, 1 diagnostics: rejected: edit 1 (App.java): the search text is not in the file
                2. symbol:broken, 1 diagnostics: accepted: 0 build errors, 1 compile errors, 0 test failures -> 0 build errors, 0 compile errors, 0 test failures
              Fixes (1):
                - $fixCommit Call fixed() instead of broken()
            """.trimIndent(),
        )
        assertTrue(stdout.indexOf("Recipe (applied)") < stdout.indexOf("Fix loop ("))
        assertTrue(stdout.indexOf("Fix loop (") < stdout.indexOf("Caused by the update (0)"))
    }

    @Test
    fun `the report lists each guardrail rejection and the count per rule, and a lower test count keeps exit 0`() {
        val stub = Edit("App.java", "broken();", "return null;")
        val adapter = FakeAdapter(testsRun = { dir -> if ("broken()" in dir.resolve("App.java").readText()) 5 else 3 })

        val result = command(llm = ScriptedLlm(stub, fix), adapter = adapter).test("$repo $args")

        assertContains(
            result.stdout,
            "1. symbol:broken, 1 diagnostics: rejected: the edits break the guardrails:\n" +
                "       - [stubbing] App.java:3: the edits add the placeholder `return null;` in place of logic\n",
        )
        assertContains(result.stdout, "  Rejections per guardrail (1):\n    - stubbing: 1\n  Tests run in the final verify: 3\n")
        assertEquals(0, result.statusCode, "the runtime test count is reported, not enforced")
    }

    @Test
    fun `what is left after the loop is reported, and exits with 1`() {
        val result = command(llm = ScriptedLlm(Edit("App.java", "nowhere", "x"), Edit("App.java", "nowhere", "y"))).test("$repo $args")

        assertContains(result.stdout, "stopped: no progress in the last attempts")
        assertContains(result.stdout, "  Fixes (0):\n    none\n")
        assertContains(result.stdout, "Caused by the update (1)\n  - [javac] App.java:3: cannot find symbol broken")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `without a recipe nothing is committed before the loop, and the request has no recipe diff`() {
        val llm = ScriptedLlm(fix)

        val result = command(index = noRecipe, llm = llm).test("$repo $args")

        assertContains(result.stdout, "Recipe (not applied)\n")
        assertTrue("Commit:" !in result.stdout.substringBefore("Fix loop"))
        assertEquals(null, llm.requests.single().recipeDiff)
        assertEquals("2", git("rev-list", "--count", "HEAD").trim())
    }

    @Test
    fun `a recipe that fails or changes nothing is not committed`() {
        listOf(FakeAdapter { RecipeRun.Failed("plugin crashed") }, FakeAdapter { RecipeRun.Applied }).forEach { adapter ->
            val result =
                command(
                    llm = ScriptedLlm(Edit("App.java", "nowhere", "x"), Edit("App.java", "nowhere", "y")),
                    adapter = adapter,
                ).test("$repo $args")

            assertTrue("Commit:" !in result.stdout.substringBefore("Fix loop"), result.stdout)
            assertEquals("1", git("rev-list", "--count", "HEAD").trim())
        }
    }

    @Test
    fun `--format json has the recipe, its commit, the fix loop, and the verification, and nothing else`() {
        val result = command(index = noRecipe, llm = ScriptedLlm(Edit("App.java", "nowhere", "x"), fix)).test("$repo $args --format json")

        val json = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(listOf("recipe", "recipeCommit", "fixLoop", "verification"), json.keys.toList())
        assertEquals(JsonNull, json.getValue("recipeCommit"))
        val loop = json.getValue("fixLoop").jsonObject
        assertEquals("nothingLeft", loop.getValue("stopReason").jsonPrimitive.content)
        val attempts = loop.getValue("attempts").jsonArray.map { it.jsonObject }
        assertEquals(listOf("rejected", "accepted"), attempts.map { it.getValue("outcome").jsonPrimitive.content })
        assertEquals("edit 1 (App.java): the search text is not in the file", attempts[0].getValue("reason").jsonPrimitive.content)
        assertEquals(JsonNull, attempts[0].getValue("commit"))
        assertEquals(git("rev-parse", "HEAD").trim(), attempts[1].getValue("commit").jsonPrimitive.content)
        assertEquals("Call fixed() instead of broken()", attempts[1].getValue("rationale").jsonPrimitive.content)
        assertEquals("symbol:broken", attempts[1].getValue("cluster").jsonPrimitive.content)
        assertEquals(
            "App.java",
            attempts[1]
                .getValue("diagnostics")
                .jsonArray[0]
                .jsonObject
                .getValue("file")
                .jsonPrimitive.content,
        )
        assertEquals(
            "fixed();",
            attempts[1]
                .getValue("edits")
                .jsonArray[0]
                .jsonObject
                .getValue("replace")
                .jsonPrimitive.content,
        )
        assertEquals(
            emptyList(),
            json
                .getValue("verification")
                .jsonObject
                .getValue("causedByUpdate")
                .jsonArray
                .toList(),
        )
        assertEquals("", result.stderr)
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `the recipe's commit is in the JSON report`() {
        val result = command(llm = ScriptedLlm(fix)).test("$repo $args --format json")

        val json = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(git("rev-parse", "HEAD~1").trim(), json.getValue("recipeCommit").jsonPrimitive.content)
    }

    @Test
    fun `the release notes file goes into every request`() {
        val notes = repo.parent.resolve("${repo.fileName}-notes.md")
        notes.writeText("3.4 renames broken() to fixed().\n")
        val llm = ScriptedLlm(fix)

        command(llm = llm).test("$repo $args --release-notes $notes")

        assertEquals("3.4 renames broken() to fixed().\n", llm.requests.single().releaseNotes)
    }

    @Test
    fun `a release notes file that doesn't exist is a usage error with exit 2`() {
        val run =
            lector().executeInProcess(
                "heal",
                repo.toString(),
                *args.split(" ").toTypedArray(),
                "--release-notes",
                "/no/such/notes.md",
            )

        assertEquals("", run.stdout)
        assertContains(run.stderr, "--release-notes")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `the Ollama URL and model default to a local Ollama and qwen2_5-coder 7b`() {
        command(llm = ScriptedLlm(fix)).test("$repo $args")

        assertEquals(listOf(URI("http://localhost:11434") to "qwen2.5-coder:7b"), clients)
    }

    @Test
    fun `the Ollama URL and model can be set`() {
        command(llm = ScriptedLlm(fix)).test("$repo $args --ollama-url http://ollama:8080 --model qwen3-coder:30b")

        assertEquals(listOf(URI("http://ollama:8080") to "qwen3-coder:30b"), clients)
    }

    @Test
    fun `the budgets can be set, and a used-up budget stops the loop`() {
        val miss = Edit("App.java", "nowhere", "x")
        val llm = ScriptedLlm(miss, miss.copy(replace = "y"), miss.copy(replace = "z"))

        val result =
            command(llm = llm).test(
                "$repo $args --max-attempts 3 --max-attempts-per-cluster 3 --max-attempts-without-progress 3 --time-limit 30",
            )

        assertEquals(3, llm.requests.size)
        assertContains(result.stdout, "stopped: the attempt budget is used up")
    }

    @Test
    fun `the diff-size limit can be set, and an attempt above it is rejected by the guardrails`() {
        val result = command(llm = ScriptedLlm(fix)).test("$repo $args --max-changed-lines 1 --max-attempts 1")

        assertContains(result.stdout, "rejected: the edits break the guardrails:")
        assertContains(result.stdout, "- [diff-size] the edits change 2 lines (added plus removed), the limit is 1")
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `help shows the defaults of the Ollama and budget options`() {
        val result = lector().test("heal --help")

        listOf(
            "--ollama-url" to "http://localhost:11434",
            "--model" to "qwen2.5-coder:7b",
            "--max-attempts-per-cluster" to "3",
            "--max-attempts" to "20",
            "--time-limit" to "30",
            "--max-attempts-without-progress" to "2",
            "--max-changed-lines" to "50",
        ).forEach { (option, default) ->
            val line =
                result.stdout
                    .lines()
                    .dropWhile { option !in it }
                    .take(3)
                    .joinToString(" ")
            assertContains(line, "(default: $default)", message = option)
        }
    }

    @Test
    fun `an --ollama-url that isn't a URL is a usage error with exit 2`() {
        val run = lector().executeInProcess("heal", repo.toString(), *args.split(" ").toTypedArray(), "--ollama-url", "http://no such host")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "invalid value for --ollama-url: not a URL")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `an --ollama-url without http or https and a host is a usage error with exit 2, before anything runs`() {
        listOf("localhost:11434", "ftp://ollama", "http:/no-host", "ollama").forEach { url ->
            val llm = ScriptedLlm()
            val run =
                LectorCommand("1.2.3")
                    .subcommands(command(llm = llm))
                    .executeInProcess("heal", repo.toString(), *args.split(" ").toTypedArray(), "--ollama-url", url)

            assertEquals("", run.stdout)
            assertContains(run.stderr, "invalid value for --ollama-url: not an http or https URL with a host", message = url)
            assertEquals(2, run.exitCode)
            assertEquals("1", git("rev-list", "--count", "HEAD").trim(), "the recipe didn't run")
        }
    }

    @Test
    fun `a budget below 1 is a usage error with exit 2`() {
        listOf("--max-attempts", "--max-changed-lines").forEach { option ->
            val run = lector().executeInProcess("heal", repo.toString(), *args.split(" ").toTypedArray(), option, "0")

            assertEquals("", run.stdout)
            assertContains(run.stderr, option)
            assertEquals(2, run.exitCode)
        }
    }

    @Test
    fun `a dirty working tree is a tool error with exit 2, and nothing runs`() {
        repo.resolve("App.java").writeText("local edit\n")
        val llm = ScriptedLlm()

        val result = command(llm = llm).test("$repo $args")

        assertEquals("", result.stdout)
        assertContains(result.stderr, "Error: $repo has uncommitted changes. Commit or stash them first: App.java")
        assertEquals(2, result.statusCode)
        assertEquals(emptyList(), llm.requests)
    }

    @Test
    fun `an unreachable Ollama is a tool error with exit 2`() {
        val port = ServerSocket(0).use { it.localPort }

        val result =
            HealCommand(adapters = { listOf(FakeAdapter()) }, index = { index })
                .test("$repo $args --ollama-url http://127.0.0.1:$port")

        assertEquals("", result.stdout)
        assertContains(result.stderr, "Error: Ollama is not reachable at http://127.0.0.1:$port")
        assertEquals(2, result.statusCode)
    }

    @Test
    fun `run through the root command, what is left exits with 1`() {
        val run =
            lector(ScriptedLlm(Edit("App.java", "nowhere", "x"), Edit("App.java", "nowhere", "y")))
                .executeInProcess("heal", repo.toString(), *args.split(" ").toTypedArray())

        assertContains(run.stdout, "Caused by the update (1)")
        assertEquals("", run.stderr)
        assertEquals(1, run.exitCode)
    }

    @Test
    fun `a build error the base has too is checked on the base once per run, and doesn't fail the job`() {
        val gpg =
            Diagnostic(
                null,
                null,
                null,
                null,
                "Failed to execute goal maven-gpg-plugin:sign: Cannot run program \"gpg\"",
                Severity.ERROR,
                "maven",
            )
        val adapter = FakeAdapter(testsRun = { 12 }, buildErrors = listOf(gpg))
        val noFix = Edit("App.java", "broken();", "broken(); // still")
        val llm = ScriptedLlm(noFix, noFix.copy(replace = "broken(); // again"), noFix.copy(replace = "broken(); // once more"))

        val result = command(llm = llm, adapter = adapter).test("$repo $args --base HEAD --max-attempts-without-progress 3 --format json")

        val report = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(
            3,
            report
                .getValue("fixLoop")
                .jsonObject
                .getValue("attempts")
                .jsonArray.size,
        )
        assertEquals(1, adapter.verified.count { it != repo }, "the first verify, the recipe's, and three attempts share one")
        assertContains(
            report
                .getValue("verification")
                .jsonObject
                .getValue("preExisting")
                .toString(),
            "maven-gpg-plugin",
        )
        assertEquals(1, result.statusCode, "the compile error is left")
    }

    private fun lector(llm: LlmClient = ScriptedLlm()) = LectorCommand("1.2.3").subcommands(command(llm = llm))

    private fun command(
        index: RecipeIndex = this.index,
        llm: LlmClient,
        adapter: FakeAdapter = FakeAdapter(),
    ) = HealCommand(
        adapters = { listOf(adapter) },
        index = { index },
        llm = { url, model ->
            clients += url to model
            llm
        },
    )

    private fun git(vararg args: String): String {
        val command = listOf("git", "-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
        val result = runProcess(command + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }
}
