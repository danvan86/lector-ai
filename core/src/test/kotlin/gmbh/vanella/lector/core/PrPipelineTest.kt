package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The PR pipeline with fakes for the host, the source, the adapter, the index, and the model, on a real Git
 * repository: a shallow clone of an origin with the base commit and the bot's update on top.
 */
class PrPipelineTest {
    @TempDir
    lateinit var dir: Path

    private val origin by lazy { dir.resolve("origin") }
    private val repo by lazy { dir.resolve("repo") }

    private val bot = CommitIdentity("lector-ai[bot]", "1+lector-ai[bot]@users.noreply.github.com")
    private val lib = PackageUpdate("test", "org.example:lib", "1.0", "2.0")
    private val other = PackageUpdate(null, "org.example:other", "3.0", "4.0")
    private val fix = Edit("App.java", "broken();", "fixed();")
    private val broken = "class App {\n    void run() {\n        broken();\n    }\n}\n"

    private var baseSha = ""
    private var headSha = ""

    @Test
    fun `a PR whose author isn't a bot login is skipped without a comment`() {
        val host = host(pr().copy(author = "mallory"))

        val outcome = pipeline(host).run(repo)

        assertEquals(PrOutcome.Skipped("the PR's author mallory is not one of the bot logins renovate[bot]"), outcome)
        assertNothingHappened(host)
    }

    @Test
    fun `a PR from a fork is skipped without a comment`() {
        val host = host(pr().copy(headRepository = "mallory/app"))

        val outcome = pipeline(host).run(repo)

        assertEquals(PrOutcome.Skipped("the PR comes from mallory/app, not from acme/app"), outcome)
        assertNothingHappened(host)
    }

    @Test
    fun `a PR on a branch the source doesn't recognize is skipped without a comment`() {
        val host = host(pr().copy(headBranch = "feature/bump"))

        val outcome = pipeline(host).run(repo)

        assertEquals(PrOutcome.Skipped("the update source doesn't recognize the branch feature/bump"), outcome)
        assertNothingHappened(host)
    }

    @Test
    fun `a PR whose head commit is the tool's own is skipped without a comment`() {
        val host = host(pr(), headAuthor = bot)

        val outcome = pipeline(host).run(repo)

        assertEquals(PrOutcome.Skipped("the head commit is the tool's own"), outcome)
        assertNothingHappened(host)
    }

    @Test
    fun `a PR without a version change is skipped without a comment`() {
        val host = host(pr())

        val outcome = pipeline(host, updates = emptyList()).run(repo)

        assertEquals(PrOutcome.Skipped("the PR changes no version of the repository's test packages"), outcome)
        assertNothingHappened(host)
    }

    @Test
    fun `changes of another ecosystem don't count`() {
        val host = host(pr())

        val outcome = pipeline(host, updates = listOf(lib.copy(ecosystem = "npm"))).run(repo)

        assertIs<PrOutcome.Skipped>(outcome)
    }

    @Test
    fun `a working tree that isn't on the PR's head is a tool error`() {
        val host = host(pr().copy(headSha = "0123456789abcdef0123456789abcdef01234567"))

        val error = assertFailsWith<ToolError> { pipeline(host).run(repo) }

        assertEquals(
            "The working tree is on $headSha, not on the PR's head 0123456789abcdef0123456789abcdef01234567. Check out the PR's head commit.",
            error.message,
        )
    }

    @Test
    fun `a repository no adapter detects is a tool error`() {
        val host = host(pr())

        val error = assertFailsWith<ToolError> { pipeline(host, adapter = FakeAdapter(detects = false)).run(repo) }

        assertEquals("No supported ecosystem in $repo", error.message)
    }

    @Test
    fun `the base commit a shallow clone left out is fetched before verify, and one that is there isn't`() {
        val host = host(pr(), app = "class App {}\n")
        assertFalse(GitWorkingTree.hasCommit(repo, baseSha))

        pipeline(host).run(repo)

        assertEquals(listOf(baseSha), host.fetches)

        val deepHost = host(pr(), app = "class App {}\n", depth = null)
        pipeline(deepHost).run(repo)
        assertEquals(emptyList(), deepHost.fetches)
    }

    @Test
    fun `a green verify stops the run with no recipe, no commit, and no new comment`() {
        val host = host(pr(), app = "class App {}\n")
        val adapter = FakeAdapter()

        val outcome = pipeline(host, adapter = adapter, recipes = mapOf(lib.`package` to recipe("UpgradeLib"))).run(repo)

        assertEquals(PrOutcome.Green(Verification()), outcome)
        assertEquals(emptyList(), adapter.recipeRuns)
        assertEquals(headSha, GitWorkingTree.head(repo))
        assertEquals(emptyList(), host.pushes)
        assertEquals(emptyList(), host.labels)
        val (body, create) = host.comments.single()
        assertFalse(create, "only an existing comment is updated")
        assertContains(body, "nothing caused by the update")
    }

    @Test
    fun `grouped changes with the same recipe run it once, as one commit`() {
        val host = host(pr())
        val adapter = FakeAdapter()
        val upgrade = recipe("UpgradeAll")

        pipeline(
            host,
            adapter = adapter,
            updates = listOf(lib, other),
            recipes = mapOf(lib.`package` to upgrade, other.`package` to upgrade),
        ).run(repo)

        assertEquals(listOf("UpgradeAll"), adapter.recipeRuns)
        assertEquals(
            listOf("Apply recipe UpgradeAll", "Call fixed()"),
            host.pushes.single(),
        )
        assertEquals(
            "For org.example:lib 1.0 -> 2.0, org.example:other 3.0 -> 4.0.",
            git("log", "-1", "--format=%b", "HEAD~1").trim(),
        )
    }

    @Test
    fun `different recipes run in the order of the PR's table, one commit each`() {
        val host = host(pr())
        val adapter = FakeAdapter()

        pipeline(
            host,
            adapter = adapter,
            updates = listOf(other, lib),
            recipes = mapOf(lib.`package` to recipe("UpgradeLib"), other.`package` to recipe("UpgradeOther")),
        ).run(repo)

        assertEquals(listOf("UpgradeOther", "UpgradeLib"), adapter.recipeRuns)
        assertEquals(listOf("Apply recipe UpgradeOther", "Apply recipe UpgradeLib", "Call fixed()"), host.pushes.single())
    }

    @Test
    fun `a change that isn't an upgrade gets no recipe, and the others still do`() {
        val host = host(pr())
        val adapter = FakeAdapter()
        val downgrade = lib.copy(fromVersion = "2.0", toVersion = "1.0")

        val outcome =
            pipeline(
                host,
                adapter = adapter,
                updates = listOf(downgrade, other),
                recipes = mapOf(lib.`package` to recipe("UpgradeLib"), other.`package` to recipe("UpgradeOther")),
            ).run(repo)

        assertEquals(listOf("UpgradeOther"), adapter.recipeRuns)
        val report = assertIs<PrOutcome.Reported>(outcome).report
        assertEquals(listOf(RecipeOutcome.NotAnUpgrade), report.withoutRecipe.map { it.second })
    }

    @Test
    fun `a recipe that fails or changes nothing makes no commit, and the comment says why`() {
        val host = host(pr())
        val adapter = FakeAdapter()

        val outcome =
            pipeline(
                host,
                adapter = adapter,
                updates = listOf(lib, other),
                recipes = mapOf(lib.`package` to recipe("FailingUpgrade"), other.`package` to recipe("NoopUpgrade")),
            ).run(repo)

        val report = assertIs<PrOutcome.Reported>(outcome).report
        assertEquals(listOf(null, null), report.recipes.map { it.commit })
        assertEquals(listOf("Call fixed()"), host.pushes.single())
        val comment = host.comments.single().first
        assertContains(comment, "`FailingUpgrade` for org.example:lib 1.0 → 2.0: the recipe run failed: boom\n")
        assertContains(comment, "`NoopUpgrade` for org.example:other 3.0 → 4.0: applied, changed nothing")
    }

    @Test
    fun `with diagnostics left, the recipe commit and every accepted fix are pushed, by the bot, and the comment lists what is left`() {
        val host = host(pr(), app = broken + "class Gone {\n    void run() {\n        missing();\n    }\n}\n")
        val llm = ScriptedLlm(edits(fix, rationale = "Call fixed()"), { Proposal.Invalid("no idea") }, { Proposal.Invalid("still none") })

        val outcome = pipeline(host, llm = llm, recipes = mapOf(lib.`package` to recipe("UpgradeLib"))).run(repo)

        val report = assertIs<PrOutcome.Reported>(outcome).report
        assertEquals(listOf("Apply recipe UpgradeLib", "Call fixed()"), host.pushes.single())
        assertEquals(1, report.verification.causedByUpdate.size, "the missing() error is left")
        assertTrue(report.pushed)
        assertEquals(
            listOf("${bot.name} <${bot.email}>"),
            git("log", "--format=%an <%ae>", "$headSha..HEAD")
                .lines()
                .filter {
                    it.isNotEmpty()
                }.distinct(),
        )
        val (body, create) = host.comments.single()
        assertTrue(create)
        assertContains(body, "UpgradeLib")
        assertContains(body, "Call fixed()")
        assertContains(body, "cannot find symbol missing")
        assertEquals(listOf("ai-assisted"), host.labels)
    }

    @Test
    fun `a rejected push ends the run without a comment or a label`() {
        val host = host(pr(), acceptsPush = false)

        val outcome = pipeline(host).run(repo)

        assertIs<PrOutcome.PushRejected>(outcome)
        assertEquals(1, host.pushes.size)
        assertEquals(emptyList(), host.comments)
        assertEquals(emptyList(), host.labels)
    }

    @Test
    fun `without a commit nothing is pushed and no label is added, but the comment reports what is left`() {
        val host = host(pr())
        val llm = ScriptedLlm({ Proposal.Invalid("no idea") }, { Proposal.Invalid("still none") })

        val outcome = pipeline(host, llm = llm).run(repo)

        val report = assertIs<PrOutcome.Reported>(outcome).report
        assertFalse(report.pushed)
        assertEquals(emptyList(), host.pushes)
        assertEquals(emptyList(), host.labels)
        assertContains(host.comments.single().first, "cannot find symbol broken")
    }

    @Test
    fun `the fix loop gets every change, the release notes, and the recipe diff`() {
        val host = host(pr())
        val llm = ScriptedLlm(edits(fix))

        pipeline(host, llm = llm, updates = listOf(lib, other), recipes = mapOf(lib.`package` to recipe("UpgradeLib"))).run(repo)

        val request = llm.requests.first()
        assertEquals(
            listOf(
                DependencyChange("test", lib.`package`, "1.0", "2.0", UpdateType.MAJOR),
                DependencyChange("test", other.`package`, "3.0", "4.0", UpdateType.MAJOR),
            ),
            request.changes,
        )
        assertEquals("lib 2.0 renames broken() to fixed()", request.releaseNotes)
        assertContains(request.recipeDiff.orEmpty(), "+UpgradeLib")
    }

    private fun assertNothingHappened(host: FakeHost) {
        assertEquals(emptyList(), host.comments)
        assertEquals(emptyList(), host.labels)
        assertEquals(emptyList(), host.pushes)
        assertEquals(emptyList(), host.fetches)
    }

    private fun pr() = PullRequest(7, "renovate[bot]", "bot/lib", "", "acme/app", "acme/app", "", "body")

    /**
     * An origin with the base commit and the bot's update on top, cloned to [repo] with [depth]. Returns the host for
     * [pr] with the real head and base.
     */
    private fun host(
        pr: PullRequest,
        app: String = broken,
        headAuthor: CommitIdentity = CommitIdentity("renovate[bot]", "29139614+renovate[bot]@users.noreply.github.com"),
        depth: Int? = 1,
        acceptsPush: Boolean = true,
    ): FakeHost {
        dir
            .toFile()
            .listFiles()
            .orEmpty()
            .forEach { it.deleteRecursively() }
        origin.toFile().mkdirs()
        git(origin, "init", "--quiet")
        origin.resolve("App.java").writeText(app)
        origin.resolve("pom.xml").writeText("org.example:lib 1.0\norg.example:other 3.0\n")
        commit(origin, CommitIdentity("Dev", "dev@example.com"), "base")
        baseSha = git(origin, "rev-parse", "HEAD").trim()
        origin.resolve("pom.xml").writeText("org.example:lib 2.0\norg.example:other 4.0\n")
        commit(origin, headAuthor, "Update lib")
        headSha = git(origin, "rev-parse", "HEAD").trim()
        val depthOption = if (depth == null) emptyList() else listOf("--depth", depth.toString())
        git(dir, listOf("clone", "--quiet") + depthOption + listOf("file://$origin", repo.toString()))
        val withShas = pr.copy(headSha = pr.headSha.ifEmpty { headSha }, baseSha = baseSha)
        return FakeHost(withShas, acceptsPush)
    }

    private fun pipeline(
        host: FakeHost,
        adapter: FakeAdapter = FakeAdapter(),
        llm: LlmClient = ScriptedLlm(edits(fix, rationale = "Call fixed()")),
        updates: List<PackageUpdate> = listOf(lib),
        recipes: Map<String, Recipe> = emptyMap(),
    ) = PrPipeline(
        host = host,
        source = FakeSource(SourceUpdate(updates, "lib 2.0 renames broken() to fixed()", BranchPolicy.IN_PLACE)),
        adapters = listOf(adapter),
        index = { change, _ ->
            recipes[change.`package`]?.let { RecipeSelection(RecipeMatch(it, it.name, 1), emptyList()) }
                ?: RecipeSelection.NONE
        },
        llm = llm,
        settings = PrSettings(botLogins = setOf("renovate[bot]"), identity = bot, budgets = Budgets(attemptsWithoutProgress = 2)),
    )

    private fun recipe(name: String) = Recipe(name, "org.example:recipes:1.0", "Apache-2.0")

    private fun edits(
        vararg edits: Edit,
        rationale: String = "Fix it",
    ): (FixRequest) -> Proposal = { Proposal.Edits(edits.toList(), rationale) }

    private fun commit(
        where: Path,
        identity: CommitIdentity,
        message: String,
    ) {
        git(where, "add", ".")
        git(where, listOf("-c", "user.name=${identity.name}", "-c", "user.email=${identity.email}", "commit", "--quiet", "-m", message))
    }

    private fun git(vararg args: String) = git(repo, args.asList())

    private fun git(
        where: Path,
        vararg args: String,
    ) = git(where, args.asList())

    private fun git(
        where: Path,
        args: List<String>,
    ): String {
        val result = runProcess(listOf("git", "-c", "commit.gpgsign=false") + args, where, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
        return result.output
    }

    /** Records what the pipeline does on the host. A push records the subjects of the commits it would push. */
    private inner class FakeHost(
        private val pr: PullRequest,
        private val acceptsPush: Boolean,
    ) : GitHost {
        val fetches = mutableListOf<String>()
        val pushes = mutableListOf<List<String>>()
        val comments = mutableListOf<Pair<String, Boolean>>()
        val labels = mutableListOf<String>()

        override fun pullRequest() = pr

        override fun fetch(
            repo: Path,
            pr: PullRequest,
            commit: String,
        ) {
            fetches += commit
            git(repo, "fetch", "--quiet", "--depth=1", "origin", commit)
        }

        override fun push(
            repo: Path,
            pr: PullRequest,
        ): Boolean {
            pushes += git(repo, "log", "--reverse", "--format=%s", "${pr.headSha}..HEAD").lines().filter { it.isNotEmpty() }
            return acceptsPush
        }

        override fun comment(
            pr: PullRequest,
            body: String,
            create: Boolean,
        ) {
            comments += body to create
        }

        override fun label(
            pr: PullRequest,
            label: String,
        ) {
            labels += label
        }
    }

    /** Recognizes `bot/` branches and reads the given update from every PR. */
    private class FakeSource(
        private val update: SourceUpdate,
    ) : UpdateSource {
        override fun recognizes(pr: PullRequest) = pr.headBranch.startsWith("bot/")

        override fun read(pr: PullRequest) = update
    }

    /** Answers with the given functions in order, and records every request. */
    private class ScriptedLlm(
        vararg answers: (FixRequest) -> Proposal,
    ) : LlmClient {
        val requests = mutableListOf<FixRequest>()
        private val answers = ArrayDeque(answers.toList())

        override fun propose(request: FixRequest): Proposal {
            requests += request
            return answers.removeFirst()(request)
        }
    }

    /**
     * Every line of `App.java` that calls `broken()` or `missing()` is a compile error, keyed by the symbol. Versions are
     * the `package version` lines of `pom.xml`. A recipe appends its name to `recipes.txt`, unless its name says it fails or
     * does nothing.
     */
    private class FakeAdapter(
        private val detects: Boolean = true,
    ) : EcosystemAdapter {
        val recipeRuns = mutableListOf<String>()

        override val ecosystem = "test"
        override val versionScheme = DottedVersions
        override val recipeRunner =
            RecipeRunner { repo, recipe ->
                recipeRuns += recipe.name
                when {
                    recipe.name.startsWith("Failing") -> {
                        RecipeRun.Failed("boom\nwith details")
                    }

                    recipe.name.startsWith("Noop") -> {
                        RecipeRun.Applied
                    }

                    else -> {
                        repo.resolve("recipes.txt").toFile().appendText("${recipe.name}\n")
                        RecipeRun.Applied
                    }
                }
            }
        override val resolvedVersions =
            ResolvedVersions { dir -> dir.resolve("pom.xml").readLines().associate { it.substringBefore(' ') to it.substringAfter(' ') } }
        override val languageSupport = LanguageSupport { _, _ -> null }

        override fun detect(repo: Path) = detects

        override fun verify(repo: Path): List<Diagnostic> =
            repo.resolve("App.java").readLines().withIndex().mapNotNull { (index, line) ->
                listOf("broken", "missing").firstOrNull { "$it()" in line }?.let {
                    Diagnostic("App.java", index + 1, 9, "symbol:$it", "cannot find symbol $it", Severity.ERROR, "javac")
                }
            }

        override fun testsRun(repo: Path): Int? = null

        override fun runTests(
            repo: Path,
            tests: List<Diagnostic>,
        ) = tests

        override fun ground(
            repo: Path,
            change: DependencyChange,
            diagnostics: List<Diagnostic>,
        ) = ""

        override fun clusterKey(diagnostic: Diagnostic) = diagnostic.code

        override fun isBuildFile(file: String) = file == "pom.xml"

        override fun isEditable(file: String) = true

        override fun testDisablingConfig(
            file: String,
            content: String,
            line: Int,
        ): String? = null
    }
}
