package gmbh.vanella.lector.core

import java.nio.file.Path

/** The [SourceContext] of a run: [repo] at the PR's head, its [base] in a separate worktree, read with [adapter]. */
internal class RepositoryContext(
    private val repo: Path,
    private val base: BaseCommit,
    private val adapter: EcosystemAdapter,
) : SourceContext {
    override val ecosystem = adapter.ecosystem

    override fun baseVersions() = base.checkout(resolved()::read)

    override fun headVersions() = resolved().read(repo)

    private fun resolved() = adapter.resolvedVersions ?: throw ToolError("The $ecosystem ecosystem can't read resolved versions")
}
