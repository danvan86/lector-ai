package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Verification
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** The report people read in CI logs: one section per category, the ones caused by the update first. */
internal object TextReport {
    fun render(verification: Verification): String =
        listOf(
            section("Caused by the update", verification.causedByUpdate, causedByUpdate(verification.causedByUpdate)),
            section("Pre-existing", verification.preExisting, items(verification.preExisting)),
            section("Flaky", verification.flaky, items(verification.flaky)),
            section("Unattributed", verification.unattributed, items(verification.unattributed)),
        ).joinToString("\n\n")

    private fun section(
        title: String,
        diagnostics: List<Diagnostic>,
        body: List<String>,
    ) = (listOf("$title (${diagnostics.size})") + body.ifEmpty { listOf("  none") }).joinToString("\n")

    // Build errors without a location are often the root cause, so they get their own group, listed first.
    private fun causedByUpdate(diagnostics: List<Diagnostic>): List<String> {
        val (buildErrors, inCode) = diagnostics.partition { it.file == null && it.test == null }
        if (buildErrors.isEmpty()) return items(inCode)
        val code = if (inCode.isEmpty()) emptyList() else listOf("  In the code:") + items(inCode)
        return listOf("  Build errors:") + items(buildErrors) + code
    }

    private fun items(diagnostics: List<Diagnostic>) = diagnostics.map(::item)

    private fun item(diagnostic: Diagnostic): String {
        val location = diagnostic.file?.let { listOfNotNull(it, diagnostic.line, diagnostic.column).joinToString(":") }
        val subject = listOfNotNull(diagnostic.test, location).joinToString(" at ")
        val lines = diagnostic.message.lines()
        val head = if (subject.isEmpty()) lines.first() else "$subject: ${lines.first()}"
        return (listOf("  - [${diagnostic.tool}] $head") + lines.drop(1).map { "      $it" }).joinToString("\n")
    }
}

/** The same [Verification] for machines: one JSON object with the four lists. Every field is present, null or not. */
internal object JsonReport {
    fun render(verification: Verification): String =
        buildJsonObject {
            put("causedByUpdate", array(verification.causedByUpdate))
            put("preExisting", array(verification.preExisting))
            put("flaky", array(verification.flaky))
            put("unattributed", array(verification.unattributed))
        }.toString()

    private fun array(diagnostics: List<Diagnostic>) = buildJsonArray { diagnostics.forEach { add(json(it)) } }

    private fun json(diagnostic: Diagnostic) =
        JsonObject(
            mapOf(
                "file" to JsonPrimitive(diagnostic.file),
                "line" to JsonPrimitive(diagnostic.line),
                "column" to JsonPrimitive(diagnostic.column),
                "code" to JsonPrimitive(diagnostic.code),
                "message" to JsonPrimitive(diagnostic.message),
                "severity" to JsonPrimitive(diagnostic.severity.name.lowercase()),
                "tool" to JsonPrimitive(diagnostic.tool),
                "test" to JsonPrimitive(diagnostic.test),
            ),
        )
}
