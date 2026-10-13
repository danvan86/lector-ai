package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic

/**
 * Root causes in javac, kotlinc, Maven, and Gradle messages, as cluster keys: `symbol:<name>`, `package:<name>`, and
 * `artifact:<group>:<artifact>`. The messages are the English ones; the container image runs the builds in that locale.
 */
internal object ClusterKeys {
    // javac: `symbol:   method greetPolitely(java.lang.String)`, `symbol:   class Strng`
    private val javacSymbol = Regex("""^symbol:\s+(.+)$""", RegexOption.MULTILINE)

    // kotlinc 2: `Unresolved reference 'capitalized' …`, kotlinc 1.9: `Unresolved reference: capitalized`
    private val kotlinReference = Regex("""Unresolved reference(?:: |\s*')([\w$]+)""")

    private val javacPackage = Regex("""^package ([\w.]+) does not exist""")

    // Maven: `Could not find artifact org.apache.commons:commons-lang3:jar:99.0.0 in central (…)`
    // Gradle: `Could not find org.apache.commons:commons-lang3:99.0.0.`
    private val missingArtifact = Regex("""Could not find (?:artifact )?([^:\s]+):([^:\s]+):""")

    fun of(diagnostic: Diagnostic): String? =
        when {
            diagnostic.test != null -> {
                null
            }

            diagnostic.tool == "maven" || diagnostic.tool == "gradle" -> {
                missingArtifact.find(diagnostic.message)?.let { "artifact:${it.groupValues[1]}:${it.groupValues[2]}" }
            }

            else -> {
                val missingPackage = javacPackage.find(diagnostic.message)?.let { it.groupValues[1] }
                if (missingPackage != null) "package:$missingPackage" else missingSymbol(diagnostic.message)?.let { "symbol:$it" }
            }
        }

    /** The name of the symbol a compiler can't find: a class, method, or field, without parameters. */
    fun missingSymbol(message: String): String? {
        val javac =
            javacSymbol.find(message)?.let {
                it.groupValues[1]
                    .substringBefore('(')
                    .trim()
                    .substringAfterLast(' ')
            }
        return javac ?: kotlinReference.find(message)?.let { it.groupValues[1] }
    }
}
