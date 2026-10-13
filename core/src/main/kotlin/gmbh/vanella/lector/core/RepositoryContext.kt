package gmbh.vanella.lector.core

import java.nio.file.Path

/**
 * The [SourceContext] of a run: [repo] at the PR's head, its [base] in a separate worktree, read with [adapter]. The
 * base's versions are read once per run: the source and the recipe step may both need them, and each read is a
 * dependency resolution.
 */
internal class RepositoryContext(
    private val repo: Path,
    private val base: BaseCommit,
    private val adapter: EcosystemAdapter,
) : SourceContext {
    private var baseVersions: Map<String, String>? = null

    override val ecosystem = adapter.ecosystem

    override fun baseVersions() = baseVersions ?: base.checkout(resolved()::read).also { baseVersions = it }

    override fun headVersions() = resolved().read(repo)

    private fun resolved() = adapter.resolvedVersions ?: throw ToolError("The $ecosystem ecosystem can't read resolved versions")
}
