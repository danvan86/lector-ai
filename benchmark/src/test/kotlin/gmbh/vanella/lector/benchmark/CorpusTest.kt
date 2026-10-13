package gmbh.vanella.lector.benchmark

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CorpusTest {
    private val base = "a".repeat(40)
    private val head = "b".repeat(40)

    /** A case as JSON, with [fields] in place of the defaults. */
    private fun case(vararg fields: Pair<String, String>): String {
        val values =
            mapOf(
                "id" to "\"guava-21\"",
                "origin" to "\"https://github.com/example/app/pull/12\"",
                "repository" to "\"https://github.com/example/app.git\"",
                "base" to "\"$base\"",
                "head" to "\"$head\"",
                "changes" to """[{"package": "com.google.guava:guava", "from": "18.0", "to": "21.0"}]""",
                "note" to "null",
            ) + fields.map { (name, value) -> name to if (name == "changes" || name == "note") value else "\"$value\"" }
        return values.entries.joinToString(", ", "{", "}") { (name, value) -> "\"$name\": $value" }
    }

    private fun corpus(vararg cases: String) = """{"cases": [${cases.joinToString(",")}]}"""

    @Test
    fun `a case has an ID, an origin, the repository, base and head commits, and the changes`() {
        val corpus = Corpus.parse(corpus(case(), case("id" to "boot-3", "origin" to "synthetic")))

        val case = corpus.cases.first()
        assertEquals("guava-21", case.id)
        assertEquals("https://github.com/example/app/pull/12", case.origin)
        assertEquals("https://github.com/example/app.git", case.repository)
        assertEquals(base, case.base)
        assertEquals(head, case.head)
        assertEquals(listOf(Change("com.google.guava:guava", "18.0", "21.0")), case.changes)
        assertEquals("synthetic", corpus.cases.last().origin)
    }

    @Test
    fun `a case may carry a note for the curator`() {
        val noted = case("note" to "\"Objects.firstNonNull was removed\"")

        assertEquals(
            "Objects.firstNonNull was removed",
            Corpus
                .parse(corpus(noted))
                .cases
                .single()
                .note,
        )
    }

    @Test
    fun `malformed JSON, a missing field, or an unknown field is rejected`() {
        listOf(
            "{",
            "{}",
            corpus(case().replace(", \"head\": \"$head\"", "")),
            corpus(case("changes" to """[{"package": "com.google.guava:guava", "from": "18.0"}]""")),
            corpus(case().removeSuffix("}") + """, "extra": 1}"""),
        ).forEach { json ->
            assertContains(assertFailsWith<IllegalArgumentException> { Corpus.parse(json) }.message.orEmpty(), "Malformed corpus")
        }
    }

    @Test
    fun `each case is checked for its ID, origin, repository, full commit hashes, and at least one change`() {
        val invalid =
            mapOf(
                case("id" to "Guava 21") to "ID Guava 21 is not lower-case kebab-case",
                case("origin" to "a PR") to "guava-21: origin is neither a PR URL nor synthetic",
                case("repository" to "git@github.com:example/app.git") to "guava-21: repository is not an https URL",
                case("base" to "abc123") to "guava-21: base is not a full commit hash",
                case("head" to "B".repeat(40)) to "guava-21: head is not a full commit hash",
                case("changes" to "[]") to "guava-21: no changes",
            )

        invalid.forEach { (case, message) ->
            assertEquals(message, assertFailsWith<IllegalArgumentException> { Corpus.parse(corpus(case)) }.message)
        }
    }

    @Test
    fun `IDs are unique`() {
        val error = assertFailsWith<IllegalArgumentException> { Corpus.parse(corpus(case(), case())) }

        assertEquals("Duplicate case IDs: guava-21", error.message)
    }

    @Test
    fun `a corpus missing from the classpath fails with its name`() {
        val error = assertFailsWith<IllegalStateException> { Corpus.load("no-such-corpus.json") }

        assertEquals("no-such-corpus.json is missing from the classpath", error.message)
    }

    @Test
    fun `the corpus has 20 to 25 cases, historical PRs and synthetic bumps`() {
        val corpus = Corpus.load()

        assertTrue(corpus.cases.size in 20..25, "${corpus.cases.size} cases")
        assertTrue(corpus.cases.any { it.origin == SYNTHETIC })
        assertTrue(corpus.cases.any { it.origin != SYNTHETIC })
    }
}
