package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.Edit
import gmbh.vanella.lector.core.FixRequest
import gmbh.vanella.lector.core.Fragment
import gmbh.vanella.lector.core.LanguageSupport
import gmbh.vanella.lector.core.LineRange
import gmbh.vanella.lector.core.LlmClient
import gmbh.vanella.lector.core.Proposal
import gmbh.vanella.lector.core.RecipeRun
import gmbh.vanella.lector.core.RecipeRunner
import gmbh.vanella.lector.core.ResolvedVersions
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.SourceFacts
import gmbh.vanella.lector.ecosystems.jvm.MavenVersionScheme
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration

// The fakes of the command tests: a model with scripted edits, and a Maven-like adapter.

internal const val PARENT = "org.springframework.boot:spring-boot-starter-parent"

/** Answers each request with the next edit, and records every request. */
internal class ScriptedLlm(
    vararg edits: Edit,
) : LlmClient {
    val requests = mutableListOf<FixRequest>()
    private val edits = ArrayDeque(edits.toList())

    override fun propose(request: FixRequest): Proposal {
        requests += request
        return Proposal.Edits(listOf(edits.removeFirst()), "Call fixed() instead of broken()")
    }
}

/**
 * Maven-like: the recipe moves the parent in `pom.xml` to 3.4.5, and versions are read from that file. Every line of
 * `App.java` that calls `broken()` is a compile error.
 */
internal class FakeAdapter(
    private val testsRun: (Path) -> Int? = { null },
    recipeRun: (Path) -> RecipeRun = { dir ->
        dir.resolve("pom.xml").writeText("<parent>3.4.5</parent>\n")
        RecipeRun.Applied
    },
) : EcosystemAdapter {
    override val ecosystem = "maven"

    // Every `return null` is a stub.
    override val languageSupport =
        LanguageSupport { _, content ->
            SourceFacts(
                stubs =
                    content.lines().withIndex().filter { "return null" in it.value }.map { (index, line) ->
                        Fragment(LineRange(index + 1, index + 1), line.trim())
                    },
            )
        }

    override fun isEditable(file: String) = true

    override fun testsRun(repo: Path) = testsRun.invoke(repo)

    override fun testDisablingConfig(
        file: String,
        content: String,
        line: Int,
    ): String? = null

    override val versionScheme = MavenVersionScheme

    override val recipeRunner = RecipeRunner { dir, _ -> recipeRun(dir) }

    override val resolvedVersions =
        ResolvedVersions { dir ->
            mapOf(
                PARENT to
                    dir
                        .resolve("pom.xml")
                        .readText()
                        .substringAfter("<parent>")
                        .substringBefore("</parent>"),
            )
        }

    override fun detect(repo: Path) = true

    override fun verify(
        repo: Path,
        timeout: Duration,
    ): List<Diagnostic> =
        repo.resolve("App.java").readText().lines().withIndex().filter { "broken()" in it.value }.map { (index, _) ->
            Diagnostic("App.java", index + 1, null, null, "cannot find symbol broken", Severity.ERROR, "javac")
        }

    override fun runTests(
        repo: Path,
        tests: List<Diagnostic>,
        timeout: Duration,
    ) = emptyList<Diagnostic>()

    override fun ground(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ) = "API"

    override fun clusterKey(diagnostic: Diagnostic) = "symbol:broken"

    override fun isBuildFile(file: String) = file == "pom.xml"
}
