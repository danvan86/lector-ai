package gmbh.vanella.lector.llm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.FixRequest
import gmbh.vanella.lector.core.PreviousAttempt
import gmbh.vanella.lector.core.Snippet
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * The prompt for one fix request. It must fit into the model's fixed context, with room for the answer. If it doesn't,
 * the API, the release notes, and the recipe diff are trimmed, in that order of priority. The errors, the code, and the
 * earlier attempts are never trimmed: without them the model can't fix anything.
 */
internal object Prompt {
    const val CONTEXT_TOKENS = 16_384
    const val ANSWER_TOKENS = 3_072

    // A conservative estimate for code: tokens are shorter than in prose.
    private const val CHARS_PER_TOKEN = 3
    const val MAX_CHARS = (CONTEXT_TOKENS - ANSWER_TOKENS) * CHARS_PER_TOKEN

    // An optional part shorter than this says too little to be worth its heading.
    private const val MIN_PART_CHARS = 200
    private const val SEPARATOR = "\n\n"
    private const val TRIMMED = "\n[trimmed to fit the context]"

    val SYSTEM =
        """
        You fix the compile errors and test failures that a dependency update caused in a software project.

        Answer with JSON only: {"analysis": "…", "rationale": "…", "edits": [{"file": "…", "search": "…", "replace": "…"}]}

        - `analysis` comes first: in a few sentences, what causes each error, what the new API offers instead, and which
          classes the fixed code uses that the file doesn't import yet.
        - `file` is the path relative to the repository root, as shown in the code sections.
        - `search` is copied exactly from the code shown, with its indentation, and occurs exactly once in the file. Keep it
          short: the lines that change, plus enough context to make it unique.
        - `replace` is the text that takes the place of `search`.
        - A class from another package needs its import. Add it with an edit of its own: search an existing import line,
          replace it with that line and the new import.
        - Make the smallest change that fixes the errors. Use the API of the new version as shown. Don't guess.
        - Never downgrade a dependency, never delete or disable tests, and never return whole files.
        - `rationale` is one line in the imperative, like a commit subject, e.g. "Use MoreObjects.firstNonNull".
        """.trimIndent()

    fun user(request: FixRequest): String {
        val change = request.change
        val intro = "The update of ${change.`package`} from ${change.fromVersion} to ${change.toVersion} broke this code. Fix these errors."
        val errors = "## Errors\n\n" + request.diagnostics.joinToString("\n", transform = ::error)
        val code = section("Code", request.snippets.joinToString(SEPARATOR, transform = ::snippet))
        val earlier =
            section(
                "Earlier attempts that failed",
                request.previousAttempts.withIndex().joinToString(SEPARATOR) {
                    attempt(it.index + 1, it.value)
                },
                "These edits were rejected, and the code is as shown above again. Don't repeat them. Fix what the errors after them show.",
            )
        val fixed = listOfNotNull(intro, errors, code, earlier)
        var budget = MAX_CHARS - SYSTEM.length - fixed.sumOf { it.length } - SEPARATOR.length * (fixed.size + 3)
        val optional =
            listOf(
                Triple("API of the new version", request.grounding, null),
                Triple("Release notes", request.releaseNotes.orEmpty(), null),
                Triple("Changes the recipe already made", request.recipeDiff.orEmpty(), "diff"),
            ).map { (title, text, fence) ->
                val part = trimmed(title, text, fence, budget)
                budget -= part?.length ?: 0
                part
            }
        return (listOfNotNull(intro, errors, code) + optional.filterNotNull() + listOfNotNull(earlier)).joinToString(SEPARATOR)
    }

    /** Whether [user] fits into the context next to the system prompt, with room for the answer. */
    fun fits(user: String) = SYSTEM.length + SEPARATOR.length + user.length <= MAX_CHARS

    /** The part with [text] cut to [budget] characters, or null if there is no text or no room for it. */
    private fun trimmed(
        title: String,
        text: String,
        fence: String?,
        budget: Int,
    ): String? {
        if (text.isBlank()) return null
        val heading = "## $title$SEPARATOR"
        val (open, close) = if (fence == null) "" to "" else "```$fence\n" to "\n```"
        val overhead = heading.length + open.length + close.length
        val room = budget - overhead - TRIMMED.length
        return when {
            overhead + text.length <= budget -> heading + open + text + close
            room >= MIN_PART_CHARS -> heading + open + text.take(room) + TRIMMED + close
            else -> null
        }
    }

    private fun section(
        title: String,
        body: String,
        intro: String? = null,
    ) = if (body.isEmpty()) null else "## $title$SEPARATOR" + intro?.let { it + SEPARATOR }.orEmpty() + body

    private fun error(diagnostic: Diagnostic): String {
        val location = diagnostic.file?.let { listOfNotNull(it, diagnostic.line, diagnostic.column).joinToString(":") }
        val subject = listOfNotNull(diagnostic.test, location).joinToString(" at ")
        val lines = diagnostic.message.lines()
        val head = if (subject.isEmpty()) lines.first() else "$subject: ${lines.first()}"
        return (listOf("- [${diagnostic.tool}] $head") + lines.drop(1).map { "  $it" }).joinToString("\n")
    }

    private fun snippet(snippet: Snippet) = "### ${snippet.file}, lines ${snippet.firstLine}-${snippet.lastLine}\n```\n${snippet.text}```"

    private fun attempt(
        number: Int,
        attempt: PreviousAttempt,
    ): String {
        val edits =
            buildJsonArray {
                attempt.edits.forEach { edit ->
                    add(
                        JsonObject(
                            mapOf(
                                "file" to JsonPrimitive(edit.file),
                                "search" to JsonPrimitive(edit.search),
                                "replace" to JsonPrimitive(edit.replace),
                            ),
                        ),
                    )
                }
            }
        return "### Attempt $number, rejected: ${attempt.failure}\n```json\n$edits\n```"
    }
}
