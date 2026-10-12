package gmbh.vanella.lector.core

import java.nio.file.Path
import kotlin.time.Duration

/** An adapter for the guardrails: every file is editable, none is a build file, and no language is known. */
object EditableEverywhere : EcosystemAdapter {
    override val ecosystem = "none"
    override val versionScheme = DottedVersions
    override val recipeRunner = null
    override val resolvedVersions = null
    override val languageSupport = LanguageSupport { _, _ -> null }

    override fun detect(repo: Path) = false

    override fun verify(
        repo: Path,
        timeout: Duration,
    ) = emptyList<Diagnostic>()

    override fun runTests(
        repo: Path,
        tests: List<Diagnostic>,
        timeout: Duration,
    ) = emptyList<Diagnostic>()

    override fun ground(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ) = ""

    override fun clusterKey(diagnostic: Diagnostic) = null

    override fun isBuildFile(file: String) = false

    override fun isEditable(file: String) = true

    override fun testsRun(repo: Path): Int? = null

    override fun testDisablingConfig(
        file: String,
        content: String,
        line: Int,
    ) = null
}
