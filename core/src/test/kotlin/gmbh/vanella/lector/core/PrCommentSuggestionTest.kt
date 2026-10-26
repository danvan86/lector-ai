package gmbh.vanella.lector.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/** M11 section 7: the PR comment shows the suggestion collapsed, marked as not applied, in a fence its diff can't close. */
class PrCommentSuggestionTest {
    private val missing = Diagnostic("README.md", 3, null, null, "broken link", Severity.ERROR, "javac")

    // A diff of a Markdown file that contains code fences of its own, the longest of four backticks.
    private val diff =
        """
        diff --git a/README.md b/README.md
        --- a/README.md
        +++ b/README.md
        @@ -1,5 +1,5 @@
         ````markdown
         ```java
        -Old.call();
        +New.call();
         ```
         ````
        """.trimIndent()

    @Test
    fun `a red run's comment shows the suggestion collapsed, marked not applied, in a fence longer than any in the diff`() {
        val comment = PrComment.render(report(Suggestion(attempt = 2, diff = diff)))

        val fenced =
            Regex("""(?s)<details>.*not applied, needs review.*attempt 2.*\n(`{5,})diff\n${Regex.escape(diff)}\n\1\n.*</details>""")
        assertContains(comment, fenced, "collapsed, marked, and fenced with more backticks than the diff's longest run of four")
    }

    @Test
    fun `a comment without a suggestion has no suggestion section`() {
        val comment = PrComment.render(report(suggestion = null))

        assertFalse("not applied, needs review" in comment)
    }

    private fun report(suggestion: Suggestion?) =
        PrReport(
            changes = listOf(DependencyChange("maven", "org.example:lib", "1.0", "2.0", UpdateType.MAJOR)),
            withoutRecipe = emptyList(),
            recipes = emptyList(),
            fixLoop =
                FixLoopResult(
                    attempts =
                        listOf(
                            Attempt("symbol:old", listOf(missing), emptyList(), AttemptOutcome.Rejected("no progress")),
                            Attempt("symbol:old", listOf(missing), emptyList(), AttemptOutcome.Rejected("no progress")),
                        ),
                    stopReason = StopReason.NO_PROGRESS,
                    verification = Verification(listOf(missing)),
                    suggestion = suggestion,
                ),
            pushed = false,
        )
}
