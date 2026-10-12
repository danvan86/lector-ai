package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import gmbh.vanella.lector.core.BaseCommit
import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.FixContext
import gmbh.vanella.lector.core.FixLoop
import gmbh.vanella.lector.core.GitBaseCommit
import gmbh.vanella.lector.core.GitWorkingTree
import gmbh.vanella.lector.core.LlmClient
import gmbh.vanella.lector.core.RecipeIndex
import gmbh.vanella.lector.core.RecipeOutcome
import gmbh.vanella.lector.core.RecipeResult
import gmbh.vanella.lector.core.SHORT_COMMIT
import gmbh.vanella.lector.core.applyRecipe
import gmbh.vanella.lector.core.attribute
import gmbh.vanella.lector.llm.OllamaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * `lector heal <repo> --package … --from … --to …`: `migrate`, then the fix loop. A recipe result that changed anything
 * becomes one local commit, so the fixes that follow are commits of their own. The exit code follows the final
 * verification, like `verify`.
 */
internal class HealCommand(
    adapters: (mavenSettings: Path?) -> List<EcosystemAdapter>,
    private val index: () -> RecipeIndex,
    private val llm: (url: URI, model: String) -> LlmClient = ::OllamaClient,
    baseCommit: (repo: Path, ref: String) -> BaseCommit = GitBaseCommit::resolve,
) : UpdateCommand("heal", adapters, baseCommit) {
    private val releaseNotes by option(
        "--release-notes",
        metavar = "<file>",
        help = "Release notes or a migration guide for the update, given to the model",
    ).path(mustExist = true, canBeDir = false, mustBeReadable = true)
    private val fixLoop by FixLoopOptions()

    override fun help(context: Context) = "Applies the recipe for a dependency update, then lets a local LLM fix what is still broken."

    override fun execute(
        adapter: EcosystemAdapter,
        base: BaseCommit?,
    ): Report {
        // A clean start means that resetting a failed attempt can never destroy anyone's work.
        GitWorkingTree.requireClean(repo)
        val change = change(adapter)
        val recipe = applyRecipe(change, adapter, index(), repo, base)
        val recipeCommit = commitRecipe(change, recipe)
        val context = FixContext(listOf(change), releaseNotes?.readText(), recipeCommit?.let { GitWorkingTree.diff(repo, it) })
        val result =
            FixLoop(
                adapter,
                llm(fixLoop.ollamaUrl, fixLoop.model),
                base,
                fixLoop.budgets(),
            ).run(repo, context, attribute(adapter, repo, base))
        val recipeText = RecipeReport.text(change, recipe) + recipeCommit?.let { "\n  Commit: ${it.take(SHORT_COMMIT)}" }.orEmpty()
        return Report(
            text = listOf(recipeText, FixLoopReport.text(result), TextReport.render(result.verification)).joinToString("\n\n"),
            json =
                JsonObject(
                    mapOf(
                        "recipe" to RecipeReport.json(change, recipe),
                        "recipeCommit" to JsonPrimitive(recipeCommit),
                        "fixLoop" to FixLoopReport.json(result),
                        "verification" to JsonReport.json(result.verification),
                    ),
                ),
            verification = result.verification,
        )
    }

    /** Commits the recipe's result, if it changed anything, and returns the commit. */
    private fun commitRecipe(
        change: DependencyChange,
        recipe: RecipeResult,
    ): String? {
        val chosen = recipe.selection.chosen
        val outcome = recipe.outcome
        if (chosen == null || outcome !is RecipeOutcome.Applied || outcome.changedFiles.isEmpty()) return null
        val subject = "Apply recipe ${chosen.recipe.name}"
        val message = "$subject\n\nFor ${change.`package`} ${change.fromVersion} -> ${change.toVersion}."
        return GitWorkingTree.commit(repo, message, outcome.changedFiles)
    }
}
