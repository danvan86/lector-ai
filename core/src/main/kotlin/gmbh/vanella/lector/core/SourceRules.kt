package gmbh.vanella.lector.core

/**
 * The guardrails on source facts. Each works on the changed files that a language knows, and only on the facts the
 * adapter's [LanguageSupport] reports: no rule here knows a language.
 */
internal data class SourceFile(
    val change: FileChange,
    val before: SourceFacts,
    val after: SourceFacts,
)

internal fun AttemptChanges.sourceFiles() =
    files.mapNotNull { change ->
        val before = change.factsBefore
        val after = change.factsAfter
        if (before == null || after == null) null else SourceFile(change, before, after)
    }

/** Parse errors: a changed file has no more parse errors than before. Facts from a file that doesn't parse can't be trusted. */
internal object ParseErrors : Guardrail {
    override fun check(attempt: AttemptChanges) =
        attempt.sourceFiles().filter { it.after.parseErrors.size > it.before.parseErrors.size }.map { (change, before, after) ->
            val first = after.parseErrors.firstOrNull { change.touches(it) } ?: after.parseErrors.first()
            Violation(
                "parse-errors",
                change.file,
                first.first,
                "the file doesn't parse: ${after.parseErrors.size} parse errors after the edits, ${before.parseErrors.size} before",
            )
        }
}

/**
 * Symbol comparison: no method is deleted with its logic. A method may go only if a current diagnostic points into it
 * (it has to change, e.g. it overrode a removed method) and its logic survives: its body was empty, or a method the
 * attempt added, in any file, has the same body (a rename or a move). An error shows that a method must change, not
 * that its logic may go.
 */
internal object SymbolComparison : Guardrail {
    override fun check(attempt: AttemptChanges): List<Violation> {
        val files = attempt.sourceFiles()
        val addedBodies = files.flatMap { lost(it.after.methods, it.before.methods, ::key) }.map { normalized(it.body) }.toSet()
        return files.flatMap { (change, before, after) ->
            lost(before.methods, after.methods, ::key)
                .filterNot { method ->
                    val pointedInto = attempt.diagnostics.any { it.file == change.file && it.line != null && it.line in method.lines }
                    pointedInto && normalized(method.body).let { it.isEmpty() || it in addedBodies }
                }.map {
                    Violation(
                        "symbol-comparison",
                        change.file,
                        it.lines.first,
                        "the edits delete the method `${qualified(it.type, it.name)}` (line ${it.lines.first} before the edits) " +
                            "and its logic. A method may only go if a current error points into it, and its body is empty or " +
                            "moves to a method the edits add",
                    )
                }
        }
    }

    private fun key(method: Method) = method.type to method.name
}

/** Test protection, deletion: no test method is deleted, whatever the diagnostics say. A test that doesn't compile is fixed. */
internal object TestDeletion : Guardrail {
    override fun check(attempt: AttemptChanges) =
        attempt.sourceFiles().flatMap { (change, before, after) ->
            lost(before.tests, after.tests) { it.type to it.name }.map {
                Violation(
                    "test-deletion",
                    change.file,
                    it.lines.first,
                    "the edits delete the test `${qualified(it.type, it.name)}` (line ${it.lines.first} before the edits). " +
                        "Tests are never deleted: make them compile and pass instead",
                )
            }
        }
}

/** Test protection, skips: no new marker that skips a test. */
internal object TestSkips : Guardrail {
    override fun check(attempt: AttemptChanges) =
        attempt.sourceFiles().flatMap { (change, before, after) ->
            lost(after.skips, before.skips) { normalized(it.text) }.map {
                Violation("test-skip", change.file, it.lines.first, "the edits add `${normalized(it.text)}`, which skips tests")
            }
        }
}

/**
 * Test protection, assertions: a test method keeps at least as many assertions as before, and an existing assertion
 * changes only if a current compile error points into it. A failing test is fixed in the code it tests, not in what it
 * expects. New assertions are fine.
 */
internal object TestAssertions : Guardrail {
    override fun check(attempt: AttemptChanges) =
        attempt.sourceFiles().flatMap { file ->
            file.before.tests.flatMap { test ->
                val after = file.after.tests.firstOrNull { (it.type to it.name) == (test.type to test.name) }
                if (after == null) emptyList() else check(file.change.file, test, after, attempt.diagnostics)
            }
        }

    private fun check(
        file: String,
        before: TestMethod,
        after: TestMethod,
        diagnostics: List<Diagnostic>,
    ): List<Violation> {
        val name = qualified(before.type, before.name)
        if (after.assertions.size < before.assertions.size) {
            val reason = "the test `$name` has ${after.assertions.size} assertions after the edits, ${before.assertions.size} before"
            return listOf(Violation("test-assertions", file, after.lines.first, reason))
        }
        val compileErrors = diagnostics.filter { it.test == null && it.file == file }
        return lost(before.assertions, after.assertions) { normalized(it.text) }
            .filterNot { assertion -> compileErrors.any { it.line != null && it.line in assertion.lines } }
            .map {
                Violation(
                    "test-assertions",
                    file,
                    it.lines.first,
                    "the edits change the assertion `${normalized(
                        it.text,
                    )}` in the test `$name` (line ${it.lines.first} before the edits), " +
                        "and no compile error points into it. A failing test is fixed in the code it tests, not in what it expects",
                )
            }
    }
}

/** Test count: the changed files have at least as many test methods as before. */
internal object TestCount : Guardrail {
    override fun check(attempt: AttemptChanges): List<Violation> {
        val files = attempt.sourceFiles()
        val before = files.sumOf { it.before.tests.size }
        val after = files.sumOf { it.after.tests.size }
        if (after >= before) return emptyList()
        return listOf(Violation("test-count", null, null, "the changed files have $after test methods after the edits, $before before"))
    }
}

/** Stubbing: no new `return null`, empty body, or TODO placeholder in a line the attempt added or changed. */
internal object Stubbing : Guardrail {
    override fun check(attempt: AttemptChanges) =
        attempt.sourceFiles().flatMap { file ->
            file.added(SourceFacts::stubs).map {
                Violation(
                    "stubbing",
                    file.change.file,
                    it.lines.first,
                    "the edits add the placeholder `${normalized(it.text)}` in place of logic",
                )
            }
        }
}

/** Suppression: no new suppressed warning or check in a line the attempt added or changed. */
internal object Suppression : Guardrail {
    override fun check(attempt: AttemptChanges) =
        attempt.sourceFiles().flatMap { file ->
            file.added(SourceFacts::suppressions).map {
                Violation("suppression", file.change.file, it.lines.first, "the edits add the suppression `${normalized(it.text)}`")
            }
        }
}

/**
 * The [fragments] in lines the attempt added or changed that the lines it replaced didn't hold already. A placeholder
 * in a line that changed around it, like the `{}` of a no-op override whose signature changed, isn't new.
 */
private fun SourceFile.added(fragments: (SourceFacts) -> List<Fragment>) =
    lost(fragments(after).filter { change.touches(it.lines) }, fragments(before).filter { change.replaced(it.lines) }) {
        normalized(it.text)
    }

/** The items of [from] whose [key] has fewer items in [to]: the ones beyond the count that is left. */
private fun <T> lost(
    from: List<T>,
    to: List<T>,
    key: (T) -> Any,
): List<T> {
    val left = to.groupingBy(key).eachCount()
    return from.groupBy(key).flatMap { (it, items) -> items.drop(left[it] ?: 0) }
}

private fun qualified(
    type: String,
    name: String,
) = if (type.isEmpty()) name else "$type.$name"

private val whitespace = Regex("""\s+""")

/** [text] with runs of whitespace as one space, so re-indenting is no change. */
private fun normalized(text: String) = text.trim().replace(whitespace, " ")
