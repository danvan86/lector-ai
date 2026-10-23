package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path

private val logger = LoggerFactory.getLogger(ApiChangesGrounding::class.java)

/**
 * The API changes between a change's old and new version ([ApiDiff]), for the classes the errors refer to: what the
 * messages name, the imports tied to a missing symbol or package, and the classes named like a missing symbol, looked
 * up in both versions. [newJars] finds the JARs of the change's group at the new version, by artifact ID. Only if the
 * errors refer to them, [oldJars] gets the same artifacts at the old version; it may fetch them. The diff covers the
 * artifacts both versions have. Anything that fails gives no API changes.
 */
internal class ApiChangesGrounding(
    private val newJars: (repo: Path, change: DependencyChange) -> Map<String, List<Path>>,
    private val oldJars: (repo: Path, change: DependencyChange, artifacts: Set<String>) -> Map<String, List<Path>>,
) {
    // The model of each JAR read in the run. A JAR of a version doesn't change.
    private val models = mutableMapOf<Path, ApiModel>()

    fun apiChanges(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ): String {
        if (change.fromVersion.isEmpty() || change.toVersion.isEmpty() || change.fromVersion == change.toVersion) return ""
        return try {
            diff(repo, change, diagnostics)
        } catch (e: IOException) {
            logger.info("No API changes for {}: can't read a JAR: {}", change.`package`, e.message)
            ""
        } catch (e: IllegalArgumentException) {
            logger.info("No API changes for {}: can't read a class: {}", change.`package`, e.message)
            ""
        }
    }

    private fun diff(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ): String {
        val newJars = newJars(repo, change)
        val references = ErrorReferences(repo, diagnostics)
        if (!refersTo(references, model(newJars.values.flatten()).classes.keys)) return ""
        val oldJars = oldJars(repo, change, newJars.keys)
        val artifacts = newJars.keys.intersect(oldJars.keys).sorted()
        val old = model(artifacts.flatMap { oldJars.getValue(it) })
        val new = model(artifacts.flatMap { newJars.getValue(it) })
        val index = old.classes.keys + new.classes.keys
        val diffed = references.classes(references.cited, index) + index.filter { simpleName(it) in references.symbols }
        return ApiChangesText.render(change, ApiDiff.of(old, new, diffed))
    }

    /** Whether the errors refer to a class of [index], or to a class in one of its packages: one that may be gone. */
    private fun refersTo(
        references: ErrorReferences,
        index: Set<String>,
    ): Boolean {
        val packages = index.map(::packageOf).toSet()
        return references.classes(references.cited, index).isNotEmpty() ||
            references.cited.any { packageOf(it) in packages } ||
            index.any { simpleName(it) in references.symbols }
    }

    private fun model(jars: List<Path>) =
        ApiModel(
            jars
                .flatMap { jar ->
                    models.getOrPut(jar) { ApiModel.read(listOf(jar)) }.classes.entries
                }.associate { it.key to it.value },
        )
}

/** The group of a Maven package `group:artifact`. */
internal fun DependencyChange.group() = `package`.substringBefore(':')
