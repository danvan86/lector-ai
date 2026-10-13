package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.Context
import gmbh.vanella.lector.core.BaseCommit
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.GitBaseCommit
import gmbh.vanella.lector.core.GitWorkingTree
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.applyRecipe
import gmbh.vanella.lector.core.attribute
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path

/**
 * `lector migrate <repo> --package … --from … --to …`: applies the recipe for the update, if there is one and it can't
 * move anything back, and then verifies the result like `verify`. The recipe is optional: whether it ran never decides
 * the exit code. Its changes stay in the working tree, uncommitted.
 */
internal class MigrateCommand(
    adapters: (BuildConfig) -> List<EcosystemAdapter>,
    private val index: () -> RecipeIndex,
    baseCommit: (repo: Path, ref: String) -> BaseCommit = GitBaseCommit::resolve,
) : UpdateCommand("migrate", adapters, baseCommit) {
    override fun help(context: Context) = "Applies the OpenRewrite recipe for a dependency update, then verifies the result."

    override fun execute(
        adapter: EcosystemAdapter,
        base: BaseCommit?,
    ): Report {
        // A clean start means that resetting a failed recipe can never destroy anyone's work.
        GitWorkingTree.requireClean(repo)
        val change = change(adapter)
        val recipe = applyRecipe(change, adapter, index(), repo, base)
        val verification = attribute(adapter, repo, base)
        return Report(
            text = RecipeReport.text(change, recipe) + "\n\n" + TextReport.render(verification),
            json = JsonObject(mapOf("recipe" to RecipeReport.json(change, recipe), "verification" to JsonReport.json(verification))),
            verification = verification,
        )
    }
}
