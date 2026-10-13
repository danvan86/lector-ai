package gmbh.vanella.lector.benchmark

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.FixRequest
import gmbh.vanella.lector.core.LanguageSupport
import gmbh.vanella.lector.core.LlmClient
import gmbh.vanella.lector.core.Proposal
import gmbh.vanella.lector.core.Recipe
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.RecipeMatch
import gmbh.vanella.lector.core.RecipeRun
import gmbh.vanella.lector.core.RecipeRunner
import gmbh.vanella.lector.core.RecipeSelection
import gmbh.vanella.lector.core.ResolvedVersions
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.runProcess
import gmbh.vanella.lector.ecosystems.jvm.MavenVersionScheme
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val LIB = "org.example:lib"
internal val UPGRADE = Recipe("org.example.UpgradeLib", "org.example:lib-recipes:1.0", "Apache-2.0")

/** Finds [UPGRADE] for [LIB], nothing for anything else. */
internal val FakeIndex =
    RecipeIndex { change, _ ->
        if (change.`package` == LIB) RecipeSelection(RecipeMatch(UPGRADE, "2.x", 1), emptyList()) else RecipeSelection.NONE
    }

/**
 * Maven-like: versions are the `package version` lines of `pom.xml`. With [LIB] at 2.0, every line of `App.java` that
 * calls `old(` is a compile error. The recipe applies [rewrite] to `App.java`. [preExisting] tests fail everywhere,
 * [flaky] ones only in the first run.
 */
internal class FakeAdapter(
    rewrite: (String) -> String = { it.replace("old(1)", "fixed(1)") },
    private val preExisting: List<Diagnostic> = emptyList(),
    private val flaky: List<Diagnostic> = emptyList(),
    private val testsRun: Int? = 3,
    private val onVerify: () -> Unit = {},
) : EcosystemAdapter {
    override val ecosystem = "maven"
    override val versionScheme = MavenVersionScheme
    override val recipeRunner =
        RecipeRunner { repo, _ ->
            val app = repo.resolve("App.java")
            app.writeText(rewrite(app.readText()))
            RecipeRun.Applied
        }
    override val resolvedVersions =
        ResolvedVersions { repo -> repo.resolve("pom.xml").readLines().associate { it.substringBefore(' ') to it.substringAfter(' ') } }
    override val languageSupport = LanguageSupport { _, _ -> null }

    override fun detect(repo: Path) = repo.resolve("pom.xml").exists()

    override fun verify(
        repo: Path,
        timeout: Duration,
    ): List<Diagnostic> {
        onVerify()
        return compileErrors(repo) + preExisting + flaky
    }

    override fun testsRun(repo: Path) = testsRun

    override fun runTests(
        repo: Path,
        tests: List<Diagnostic>,
        timeout: Duration,
    ) = tests.filter { it in preExisting }

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

    private fun compileErrors(repo: Path): List<Diagnostic> {
        if (resolvedVersions.read(repo)[LIB] != "2.0") return emptyList()
        return repo.resolve("App.java").readLines().withIndex().filter { "old(" in it.value }.map { (index, _) ->
            Diagnostic("App.java", index + 1, 9, "symbol:old", "cannot find symbol old", Severity.ERROR, "javac")
        }
    }
}

/** Answers with the given functions in order, and records every request. A request [fits] if the function says so. */
internal class ScriptedLlm(
    vararg answers: (FixRequest) -> Proposal,
    private val fits: (FixRequest) -> Boolean = { true },
) : LlmClient {
    val requests = mutableListOf<FixRequest>()
    private val answers = ArrayDeque(answers.toList())

    override fun fits(request: FixRequest) = fits.invoke(request)

    override fun propose(request: FixRequest): Proposal {
        requests += request
        return answers.removeFirst()(request)
    }
}

internal fun git(
    repo: Path,
    vararg args: String,
): String {
    val command = listOf("git", "-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
    val result = runProcess(command + args, repo, 30.seconds)
    assertEquals(0, result.exitCode, result.output)
    return result.output
}
