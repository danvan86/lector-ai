package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.Context
import gmbh.vanella.lector.core.BaseCommit
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.GitBaseCommit
import gmbh.vanella.lector.core.attribute
import java.nio.file.Path

/** `lector verify <repo>`: builds and tests the repository, attributes every problem, and prints the report. */
internal class VerifyCommand(
    adapters: (BuildConfig) -> List<EcosystemAdapter>,
    baseCommit: (repo: Path, ref: String) -> BaseCommit = GitBaseCommit::resolve,
) : RepositoryCommand("verify", adapters, baseCommit) {
    override fun help(context: Context) = "Tells whether a dependency update broke the build or the tests, and where."

    override fun execute(
        adapter: EcosystemAdapter,
        base: BaseCommit?,
    ): Report {
        val verification = attribute(adapter, repo, base)
        return Report(TextReport.render(verification), JsonReport.json(verification), verification)
    }
}
