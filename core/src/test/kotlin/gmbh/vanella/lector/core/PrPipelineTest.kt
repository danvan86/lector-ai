package gmbh.vanella.lector.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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

    private var budgets = Budgets(attemptsWithoutProgress = 2)
    private var baseSha = ""
    private var headSha = ""

    @Test
    fun `a PR no source recognizes is skipped without a comment`() {
        val host = host(pr().copy(author = "mallory"))

        val outcome = pipeline(host).run(repo)

        assertEquals(PrOutcome.Skipped("no update source recognizes the PR by mallory on bot/lib"), outcome)
        assertNothingHappened(host)
    }

    @Test
    fun `a PR from a fork is skipped without a comment, also with a source named`() {
        val host = host(pr().copy(headRepository = "mallory/app"))

        val outcome = pipeline(host).run(repo)
        val named = pipeline(host, source = "bot").run(repo)

        assertEquals(PrOutcome.Skipped("the PR comes from mallory/app, not from acme/app"), outcome)
        assertEquals(outcome, named)
        assertNothingHappened(host)
    }

    @Test
    fun `the first source that recognizes the PR takes it`() {
        val host = host(pr().copy(labels = setOf("lector")))
        val first = FakeSource("bot", update = SourceUpdate(listOf(lib), null))
        val second = FakeSource("label", recognizes = { "lector" in it.labels }, update = SourceUpdate(listOf(other), null))

        val outcome = pipeline(host, sources = listOf(first, second)).run(repo)

        assertEquals(listOf(lib.`package`), assertIs<PrOutcome.Reported>(outcome).report.changes.map { it.`package` })
        assertEquals(1, first.reads)
        assertEquals(0, second.reads)
    }

    @Test
    fun `a named source takes the PR instead of the first, if it recognizes it`() {
        val labelled = host(pr().copy(labels = setOf("lector")))
        val first = FakeSource("bot", update = SourceUpdate(listOf(lib), null))
        val second = FakeSource("label", recognizes = { "lector" in it.labels }, update = SourceUpdate(listOf(other), null))

        val outcome = pipeline(labelled, sources = listOf(first, second), source = "label").run(repo)

        assertEquals(listOf(other.`package`), assertIs<PrOutcome.Reported>(outcome).report.changes.map { it.`package` })
        assertEquals(0, first.reads)

        val unlabelled = host(pr())
        val skipped = pipeline(unlabelled, sources = listOf(first, second), source = "label").run(repo)

        assertEquals(PrOutcome.Skipped("the label source doesn't recognize the PR by renovate[bot] on bot/lib"), skipped)
        assertNothingHappened(unlabelled)
    }

    @Test
    fun `a named source that doesn't exist is a tool error`() {
        val host = host(pr())

        val error = assertFailsWith<ToolError> { pipeline(host, source = "snyk").run(repo) }

        assertEquals("No update source is called snyk. The sources are bot.", error.message)
    }

    @Test
    fun `a source can read the resolved versions of the base, in a worktree that is removed afterwards, and of the head`() {
        val host = host(pr())
        val versions = mutableListOf<Map<String, String>>()
        val source =
            object : UpdateSource by FakeSource("bot", update = SourceUpdate(listOf(lib), null)) {
                override fun read(
                    pr: PullRequest,
                    context: SourceContext,
                ): SourceUpdate {
                    versions += context.baseVersions()
                    versions += context.headVersions()
                    assertEquals("test", context.ecosystem)
                    return SourceUpdate(listOf(lib), null)
                }
            }

        pipeline(host, sources = listOf(source)).run(repo)

        assertEquals(
            listOf(mapOf(lib.`package` to "1.0", other.`package` to "3.0"), mapOf(lib.`package` to "2.0", other.`package` to "4.0")),
            versions,
        )
        assertEquals(1, git("worktree", "list").lines().count { it.isNotEmpty() })
    }

    @Test
    fun `resolved versions of an ecosystem that can't read them are a tool error`() {
        val host = host(pr())
        val source =
            object : UpdateSource by FakeSource("bot", update = SourceUpdate(listOf(lib), null)) {
                override fun read(
                    pr: PullRequest,
                    context: SourceContext,
                ) = SourceUpdate(listOf(lib.copy(toVersion = context.headVersions().getValue(lib.`package`))), null)
            }

        val error =
            assertFailsWith<ToolError> { pipeline(host, adapter = FakeAdapter(readsVersions = false), sources = listOf(source)).run(repo) }

        assertEquals("The test ecosystem can't read resolved versions", error.message)
    }

    @Test
    fun `a source that can't read the changes fails the run, before verify, and the comment says why`() {
        val host = host(pr())
        val adapter = FakeAdapter()
        val source =
            object : UpdateSource by FakeSource("bot", update = SourceUpdate(listOf(lib), null)) {
                override fun read(
                    pr: PullRequest,
                    context: SourceContext,
                ): SourceUpdate = throw ToolError("The resolved versions of the base commit can't be read: mvn failed")
            }

        val error = assertFailsWith<ToolError> { pipeline(host, adapter = adapter, sources = listOf(source)).run(repo) }

        assertEquals("The resolved versions of the base commit can't be read: mvn failed", error.message)
        assertEquals(0, adapter.verifies)
        assertEquals(
            listOf(
                "### lector-ai\n\nlector-ai can't tell what the PR changes, so it changed nothing: " +
                    "The resolved versions of the base commit can't be read: mvn failed" to true,
            ),
            host.comments,
        )
        assertEquals(emptyList(), host.pushes)
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
        assertEquals(emptyList(), host.comments)
        assertEquals(emptyList(), host.pushes)
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
        assertEquals(listOf(7 to "ai-assisted"), host.labels)
    }

    @Test
    fun `a build past the time budget ends the loop, and the earlier fixes are still pushed and reported`() {
        val host = host(pr(), app = broken + "class Gone {\n    void run() {\n        missing();\n    }\n}\n")
        val adapter = FakeAdapter(slowWhen = "gone();")
        val llm = ScriptedLlm(edits(fix, rationale = "Call fixed()"), edits(Edit("App.java", "missing();", "gone();")))

        budgets = Budgets(time = 3.seconds)

        val outcome = pipeline(host, adapter = adapter, llm = llm).run(repo)

        val report = assertIs<PrOutcome.Reported>(outcome).report
        assertEquals(StopReason.TIME_BUDGET, report.fixLoop.stopReason)
        assertEquals(listOf("Call fixed()"), host.pushes.single())
        assertContains(host.comments.single().first, "stopped: the time budget is used up")
        assertEquals("", git("status", "--porcelain"))
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

    @Test
    fun `with no mode set, a companion source's fixes go to heal-branch, in one PR into the source branch, labelled there`() {
        val host = host(pr())

        val outcome = pipeline(host, sources = listOf(companionSource()), recipes = mapOf(lib.`package` to recipe("UpgradeLib"))).run(repo)

        val report = assertIs<PrOutcome.Reported>(outcome).report
        assertEquals(20, report.companion)
        assertEquals(emptyList(), host.pushes, "the source branch is never pushed to")
        assertEquals(listOf("heal/bot/lib" to listOf("Apply recipe UpgradeLib", "Call fixed()")), host.forcePushes)
        val pull = host.pulls.getValue(20)
        assertEquals(Pull("heal/bot/lib", "bot/lib", "lector-ai fixes for #7", pull.body, open = true), pull)
        assertContains(pull.body, "lector-ai's fixes for #7 (`bot/lib`). Merge this PR first, then #7.\n\n### lector-ai\n\n")
        assertContains(pull.body, "lector-ai pushed 2 commits; nothing caused by the update is left.")
        assertEquals(listOf(20 to "ai-assisted"), host.labels)
        val (comment, create) = host.comments.single()
        assertTrue(create)
        assertContains(comment, "lector-ai pushed 2 commits to the companion PR #20; nothing caused by the update is left.")
        assertContains(comment, "Merge the companion PR #20 into this PR's branch first, then this PR.")
    }

    @Test
    fun `a set mode overrides the source's branch policy`() {
        val inPlace = host(pr())
        pipeline(inPlace, sources = listOf(companionSource()), mode = BranchPolicy.IN_PLACE).run(repo)

        assertEquals(listOf(listOf("Call fixed()")), inPlace.pushes)
        assertEquals(emptyList(), inPlace.forcePushes)
        assertEquals(listOf(7 to "ai-assisted"), inPlace.labels)

        val companion = host(pr())
        pipeline(companion, mode = BranchPolicy.COMPANION).run(repo)

        assertEquals(emptyList(), companion.pushes)
        assertEquals(listOf("heal/bot/lib" to listOf("Call fixed()")), companion.forcePushes)
    }

    @Test
    fun `a new source head rebuilds heal-branch from it by force, and keeps the same companion PR with a new description`() {
        val host = host(pr())
        host.pulls[20] = Pull("heal/bot/lib", "bot/lib", "lector-ai fixes for #7", "old summary", open = true)

        val outcome = pipeline(host, sources = listOf(companionSource())).run(repo)

        assertEquals(20, assertIs<PrOutcome.Reported>(outcome).report.companion)
        assertEquals(listOf("heal/bot/lib" to listOf("Call fixed()")), host.forcePushes)
        assertEquals(setOf(20), host.pulls.keys, "no second companion PR")
        assertContains(host.pulls.getValue(20).body, "lector-ai pushed 1 commit;")
        assertEquals(listOf(20 to "ai-assisted"), host.labels)
    }

    @Test
    fun `a run without a commit creates no companion PR`() {
        val host = host(pr())
        val llm = ScriptedLlm({ Proposal.Invalid("no idea") }, { Proposal.Invalid("still none") })

        val outcome = pipeline(host, llm = llm, sources = listOf(companionSource())).run(repo)

        val report = assertIs<PrOutcome.Reported>(outcome).report
        assertEquals(null, report.companion)
        assertEquals(null, report.closedCompanion)
        assertEquals(emptyList(), host.forcePushes)
        assertEquals(emptyMap(), host.pulls)
        assertEquals(emptyList(), host.labels)
        assertEquals(emptyList(), host.deleted)
        assertContains(host.comments.single().first, "lector-ai pushed nothing;")
    }

    @Test
    fun `a run without a commit closes the companion PR of an earlier head, and the comment says why`() {
        val host = host(pr())
        host.pulls[20] = Pull("heal/bot/lib", "bot/lib", "lector-ai fixes for #7", "old summary", open = true)
        val llm = ScriptedLlm({ Proposal.Invalid("no idea") }, { Proposal.Invalid("still none") })

        val outcome = pipeline(host, llm = llm, sources = listOf(companionSource())).run(repo)

        assertEquals(20, assertIs<PrOutcome.Reported>(outcome).report.closedCompanion)
        assertEquals(emptyList(), host.forcePushes)
        assertFalse(host.pulls.getValue(20).open)
        assertEquals(listOf("heal/bot/lib"), host.deleted)
        assertContains(
            host.comments.single().first,
            "lector-ai pushed nothing; **1 problem caused by the update is left to fix.**\n\n" +
                "lector-ai closed its companion PR #20 and deleted its branch: this run has no fixes for the PR's head.",
        )
    }

    @Test
    fun `an in-place run without a commit doesn't touch a companion PR`() {
        val host = host(pr())
        host.pulls[20] = Pull("heal/bot/lib", "bot/lib", "lector-ai fixes for #7", "old summary", open = true)
        val llm = ScriptedLlm({ Proposal.Invalid("no idea") }, { Proposal.Invalid("still none") })

        pipeline(host, llm = llm).run(repo)

        assertTrue(host.pulls.getValue(20).open)
        assertEquals(emptyList(), host.deleted)
    }

    @Test
    fun `a green run closes the companion PR, deletes its branch, and the comment says why`() {
        val host = host(pr(), app = "class App {}\n")
        host.pulls[20] = Pull("heal/bot/lib", "bot/lib", "lector-ai fixes for #7", "old summary", open = true)

        val outcome = pipeline(host, sources = listOf(companionSource())).run(repo)

        assertEquals(PrOutcome.Green(Verification(), closedCompanion = 20), outcome)
        assertFalse(host.pulls.getValue(20).open)
        assertEquals(listOf("heal/bot/lib"), host.deleted)
        assertContains(
            host.comments.single().first,
            "lector-ai closed its companion PR #20 and deleted its branch: verify is green without it.",
        )
    }

    @Test
    fun `a green run without a companion PR closes nothing`() {
        val host = host(pr(), app = "class App {}\n")

        val outcome = pipeline(host, sources = listOf(companionSource())).run(repo)

        assertEquals(PrOutcome.Green(Verification()), outcome)
        assertEquals(emptyList(), host.deleted)
        assertFalse("companion" in host.comments.single().first)
    }

    @Test
    fun `a closed or merged source PR gets its companion PR closed and its branch deleted, without a build`() {
        listOf(PrState.CLOSED to "closed", PrState.MERGED to "merged").forEach { (state, word) ->
            val host = host(pr().copy(state = state))
            host.pulls[20] = Pull("heal/bot/lib", "bot/lib", "lector-ai fixes for #7", "summary", open = true)
            val adapter = FakeAdapter()

            val outcome = pipeline(host, adapter = adapter, sources = listOf(companionSource())).run(repo)

            assertEquals(PrOutcome.Closed(state, companion = 20), outcome)
            assertFalse(host.pulls.getValue(20).open)
            assertEquals(listOf("heal/bot/lib"), host.deleted)
            assertEquals(0, adapter.verifies)
            assertEquals(
                listOf("### lector-ai\n\nlector-ai closed its companion PR #20 and deleted its branch: this PR was $word." to false),
                host.comments,
            )
        }
    }

    @Test
    fun `a closed source PR whose companion PR was merged first gets the companion branch deleted, without a comment`() {
        val host = host(pr().copy(state = PrState.MERGED))
        host.pulls[20] = Pull("heal/bot/lib", "bot/lib", "lector-ai fixes for #7", "summary", open = false)

        val outcome = pipeline(host, sources = listOf(companionSource())).run(repo)

        assertEquals(PrOutcome.Closed(PrState.MERGED, companion = null), outcome)
        assertEquals(listOf("heal/bot/lib"), host.deleted)
        assertNothingHappened(host)
    }

    @Test
    fun `a closed PR from a fork changes nothing`() {
        val fork = host(pr().copy(state = PrState.CLOSED, headRepository = "mallory/app"))
        fork.pulls[20] = Pull("heal/bot/lib", "bot/lib", "lector-ai fixes for #7", "summary", open = true)

        assertIs<PrOutcome.Skipped>(pipeline(fork).run(repo))
        assertTrue(fork.pulls.getValue(20).open)
        assertEquals(emptyList(), fork.deleted)
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

    @Suppress("LongParameterList") // Every test sets only what it is about.
    private fun pipeline(
        host: FakeHost,
        adapter: FakeAdapter = FakeAdapter(),
        llm: LlmClient = ScriptedLlm(edits(fix, rationale = "Call fixed()")),
        updates: List<PackageUpdate> = listOf(lib),
        recipes: Map<String, Recipe> = emptyMap(),
        sources: List<UpdateSource> = listOf(FakeSource("bot", update = SourceUpdate(updates, "lib 2.0 renames broken() to fixed()"))),
        source: String? = null,
        mode: BranchPolicy? = null,
    ) = PrPipeline(
        host = host,
        sources = sources,
        adapters = listOf(adapter),
        index = { change, _ ->
            recipes[change.`package`]?.let { RecipeSelection(RecipeMatch(it, it.name, 1), emptyList()) }
                ?: RecipeSelection.NONE
        },
        llm = llm,
        settings = PrSettings(identity = bot, budgets = budgets, source = source, mode = mode),
    )

    private fun companionSource() = FakeSource("bot", BranchPolicy.COMPANION, update = SourceUpdate(listOf(lib), null))

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

    /**
     * Records what the pipeline does on the host. A push records the subjects of the commits it would push, a force-push
     * the branch and those subjects. PRs live in [pulls]; the source PR is number 7, a created PR gets the next number
     * from 20 on.
     */
    private inner class FakeHost(
        private val pr: PullRequest,
        private val acceptsPush: Boolean,
    ) : GitHost {
        val fetches = mutableListOf<String>()
        val pushes = mutableListOf<List<String>>()
        val forcePushes = mutableListOf<Pair<String, List<String>>>()
        val comments = mutableListOf<Pair<String, Boolean>>()
        val labels = mutableListOf<Pair<Int, String>>()
        val pulls = mutableMapOf<Int, Pull>()
        val deleted = mutableListOf<String>()

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
            number: Int,
            label: String,
        ) {
            labels += number to label
        }

        override fun forcePush(
            repo: Path,
            branch: String,
        ) {
            forcePushes += branch to git(repo, "log", "--reverse", "--format=%s", "${pr.headSha}..HEAD").lines().filter { it.isNotEmpty() }
        }

        override fun findPullRequest(
            head: String,
            base: String,
        ) = pulls.entries.firstOrNull { (_, pull) -> pull.head == head && pull.base == base && pull.open }?.key

        override fun createPullRequest(
            head: String,
            base: String,
            title: String,
            body: String,
        ): Int {
            val number = 20 + pulls.size
            pulls[number] = Pull(head, base, title, body, open = true)
            return number
        }

        override fun updatePullRequest(
            number: Int,
            body: String,
        ) {
            pulls[number] = pulls.getValue(number).copy(body = body)
        }

        override fun closePullRequest(number: Int) {
            pulls[number] = pulls.getValue(number).copy(open = false)
        }

        override fun deleteBranch(branch: String) {
            deleted += branch
        }
    }

    private data class Pull(
        val head: String,
        val base: String,
        val title: String,
        val body: String,
        val open: Boolean,
    )

    /** Recognizes Renovate's PRs on `bot/` branches, unless told otherwise, and reads the given update from every PR. */
    private class FakeSource(
        override val name: String,
        override val branchPolicy: BranchPolicy = BranchPolicy.IN_PLACE,
        private val recognizes: (PullRequest) -> Boolean = { it.author == "renovate[bot]" && it.headBranch.startsWith("bot/") },
        private val update: SourceUpdate,
    ) : UpdateSource {
        var reads = 0

        override fun recognizes(pr: PullRequest) = recognizes.invoke(pr)

        override fun read(
            pr: PullRequest,
            context: SourceContext,
        ): SourceUpdate {
            reads++
            return update
        }
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
     * does nothing. A build of code with [slowWhen] in it runs until its timeout, like a build that hangs.
     */
    private class FakeAdapter(
        private val detects: Boolean = true,
        private val slowWhen: String? = null,
        readsVersions: Boolean = true,
    ) : EcosystemAdapter {
        val recipeRuns = mutableListOf<String>()
        var verifies = 0

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
            if (readsVersions) {
                ResolvedVersions { dir ->
                    dir.resolve("pom.xml").readLines().associate { it.substringBefore(' ') to it.substringAfter(' ') }
                }
            } else {
                null
            }
        override val languageSupport = LanguageSupport { _, _ -> null }

        override fun detect(repo: Path) = detects

        override fun verify(
            repo: Path,
            timeout: Duration,
        ): List<Diagnostic> {
            verifies++
            val code = repo.resolve("App.java").readText()
            if (slowWhen != null && slowWhen in code) {
                // Until the timeout has passed on the loop's clock: a sleep in whole milliseconds can end just before it.
                val deadline = TimeSource.Monotonic.markNow() + timeout
                while (deadline.hasNotPassedNow()) Thread.sleep(1)
                throw ProcessTimedOut("mvn did not finish within $timeout")
            }
            return code.lines().withIndex().mapNotNull { (index, line) ->
                listOf("broken", "missing").firstOrNull { "$it()" in line }?.let {
                    Diagnostic("App.java", index + 1, 9, "symbol:$it", "cannot find symbol $it", Severity.ERROR, "javac")
                }
            }
        }

        override fun testsRun(repo: Path): Int? = null

        override fun runTests(
            repo: Path,
            tests: List<Diagnostic>,
            timeout: Duration,
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
