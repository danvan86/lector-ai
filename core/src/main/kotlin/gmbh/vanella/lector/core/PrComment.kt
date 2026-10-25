package gmbh.vanella.lector.core

// A comment has a size limit, and a reviewer reads the first errors anyway.
private const val MAX_ITEMS = 50

// A labelled PR can change hundreds of transitive packages. Most of them have no recipe.
private const val MAX_WITHOUT_RECIPE = 10

/**
 * The report comment on the PR, in Markdown (concept section 8.3): what the run did, what the update left broken, and
 * what isn't the update's fault, so a reviewer sees all of it in one place.
 */
object PrComment {
    private const val TITLE = "### lector-ai"

    fun render(report: PrReport): String {
        val fixLoop = report.fixLoop
        val left = report.verification.causedByUpdate
        val merge = report.companion?.let { "Merge the companion PR #$it into this PR's branch first, then this PR." }
        val closed = report.closedCompanion?.let { companionClosed(it, "this run has no fixes for the PR's head") }
        val sections =
            listOf(
                TITLE,
                headline(report),
                merge,
                closed,
                "#### Recipes\n" + recipes(report).ifEmpty { listOf("- none") }.joinToString("\n"),
                "#### Fixes\n" + fixes(fixLoop).ifEmpty { listOf("- none") }.joinToString("\n"),
                "The fix loop made ${count(fixLoop.attempts.size, "attempt")} and stopped: ${fixLoop.stopReason.description}.",
                if (left.isEmpty()) null else "#### Left to fix\n" + DiagnosticItems.items(left),
                fixLoop.suggestion?.let(::suggestion),
                DiagnosticItems.notCausedByUpdate(report.verification),
                fixLoop.rejectionsPerRule().map { (rule, count) -> "- $rule: $count" }.ifEmpty { null }?.let {
                    "#### Guardrail rejections\n" + it.joinToString("\n")
                },
                "Tests run in the last verify: ${report.verification.testsRun ?: "unknown"}. Review every commit: lector-ai never merges.",
            )
        return sections.filterNotNull().joinToString("\n\n")
    }

    /** The comment of a run that stopped because verify of [commit] was green, with the line on a [closedCompanion]. */
    fun green(
        verification: Verification,
        commit: String,
        closedCompanion: String?,
    ): String {
        val headline = "Verify is green on ${commit.take(SHORT_COMMIT)}: nothing caused by the update. lector-ai changed nothing."
        return listOfNotNull(TITLE, headline, closedCompanion, DiagnosticItems.notCausedByUpdate(verification)).joinToString("\n\n")
    }

    /** The comment of a run that couldn't read what the PR changes, for the [reason] the source gave. */
    fun unreadable(reason: String) = note("lector-ai can't tell what the PR changes, so it changed nothing: $reason")

    /** A comment that is only [text]. */
    fun note(text: String) = "$TITLE\n\n$text"

    /** Why the tool closed companion PR [number] and deleted its branch. */
    fun companionClosed(
        number: Int,
        reason: String,
    ) = "lector-ai closed its companion PR #$number and deleted its branch: $reason."

    /** The description of the companion PR of [source]: where it goes, the merge order, and the summary of [report]. */
    fun companionDescription(
        report: PrReport,
        source: PullRequest,
    ) = "lector-ai's fixes for #${source.number} (`${source.headBranch}`). Merge this PR first, then #${source.number}.\n\n" +
        render(report)

    private fun headline(report: PrReport): String {
        val single = report.changes.singleOrNull()
        val update = if (single == null) "${report.changes.size} packages" else change(single)
        val commits = report.recipes.count { it.commit != null } + report.fixLoop.fixes.size
        val companion = if (report.companion == null) "" else " to the companion PR #${report.companion}"
        val pushed = if (report.pushed) "pushed ${count(commits, "commit")}$companion" else "pushed nothing"
        val left = report.verification.causedByUpdate.size
        val verdict =
            when (left) {
                0 -> "nothing caused by the update is left."
                1 -> "**1 problem caused by the update is left to fix.**"
                else -> "**$left problems caused by the update are left to fix.**"
            }
        return "The update of $update broke the build. lector-ai $pushed; $verdict"
    }

    /** The [suggestion], collapsed: a reviewer opens it to finish the fix by hand. */
    private fun suggestion(suggestion: Suggestion): String {
        val fence = fence(suggestion.diff)
        return "<details>\n<summary>Suggestion, not applied, needs review: the edits of attempt ${suggestion.attempt}</summary>\n\n" +
            "The rejected attempt that got furthest. lector-ai didn't commit it: it didn't pass the progress check.\n\n" +
            "${fence}diff\n${suggestion.diff.trimEnd('\n')}\n$fence\n\n</details>"
    }

    private fun fixes(fixLoop: FixLoopResult) = fixLoop.fixes.map { "- ${it.commit.take(SHORT_COMMIT)} ${it.rationale}" }

    private fun recipes(report: PrReport): List<String> {
        val applied =
            report.recipes.map { application ->
                val changes = application.changes.joinToString(", ", transform = ::change)
                val result = result(application.outcome, application.changes.first(), application.commit)
                "- `${application.recipe.name}` for $changes: $result"
            }
        val without =
            report.withoutRecipe.take(MAX_WITHOUT_RECIPE).map { (change, outcome) ->
                "- No recipe for ${change(change)}: ${result(outcome, change, null)}"
            }
        val more = report.withoutRecipe.size - MAX_WITHOUT_RECIPE
        return applied + without + if (more > 0) listOf("- and ${count(more, "more change")} without a recipe") else emptyList()
    }

    /** What became of a recipe: its commit, or the first line of why it didn't run or was discarded. */
    private fun result(
        outcome: RecipeOutcome,
        change: DependencyChange,
        commit: String?,
    ): String {
        val reason = outcome.reason(change)
        return when {
            commit != null -> "applied in ${commit.take(SHORT_COMMIT)}"
            reason == null -> "applied, changed nothing"
            else -> reason.lines().first()
        }
    }
}

/** The diagnostics in a comment: one line each, at most [MAX_ITEMS] per list. */
private object DiagnosticItems {
    fun notCausedByUpdate(verification: Verification): String? {
        val groups =
            listOf(
                "Pre-existing (fail on the base commit too)" to verification.preExisting,
                "Flaky (passed on a rerun)" to verification.flaky,
                "Unattributed (the base commit couldn't tell)" to verification.unattributed,
            ).filter { it.second.isNotEmpty() }
        if (groups.isEmpty()) return null
        return "#### Not caused by the update\n" + groups.joinToString("\n\n") { (title, diagnostics) -> "$title:\n" + items(diagnostics) }
    }

    fun items(diagnostics: List<Diagnostic>): String {
        val shown = diagnostics.take(MAX_ITEMS).map(::item)
        val more = if (diagnostics.size > MAX_ITEMS) listOf("- and ${diagnostics.size - MAX_ITEMS} more") else emptyList()
        return (shown + more).joinToString("\n")
    }

    private fun item(diagnostic: Diagnostic): String {
        val location = diagnostic.file?.let { listOfNotNull(it, diagnostic.line).joinToString(":") }
        val subject = listOfNotNull(diagnostic.test, location).joinToString(" at ")
        val message = diagnostic.message.lines().first()
        return "- `[${diagnostic.tool}]` " + if (subject.isEmpty()) message else "$subject: $message"
    }
}

private fun change(change: DependencyChange) = change.describe()

private val backticks = Regex("`+")

// Markdown's shortest code fence.
private const val MIN_FENCE = 3

/** A code fence for [text]: longer than any run of backticks in it, so nothing in it closes the fence. */
private fun fence(text: String) = "`".repeat((backticks.findAll(text).map { it.value.length + 1 } + MIN_FENCE).max())

private fun count(
    number: Int,
    noun: String,
) = if (number == 1) "1 $noun" else "$number ${noun}s"
