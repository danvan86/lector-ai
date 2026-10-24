package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.UpdateType
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.hours

/**
 * A Gradle build's API changes (M10): the old version's JARs come from a model the run already read, the base
 * commit's in a PR, so grounding starts no Gradle of its own. The base's build doesn't leak into the head's.
 */
class GradleApiChangesTest : FakeGradleTest() {
    @TempDir
    lateinit var base: Path

    private val change = DependencyChange("maven", "org.example:lib-core", "1.0", "2.0", UpdateType.MAJOR)
    private val error =
        Diagnostic(
            "src/main/java/App.java",
            3,
            9,
            null,
            "cannot find symbol\nsymbol:   method greetPolitely(java.lang.String)\nlocation: variable g of type org.example.lib.Greeter",
            Severity.ERROR,
            "javac",
        )

    @Test
    fun `the old version's JARs come from the base commit's model, without another Gradle run`() {
        val (old, new) = jars()
        gradleProject()
        gradleProject(dir = base)
        val adapter = adapter()
        query = model(old, "1.0")
        adapter.resolvedVersions.read(base)
        query = model(new, "2.0")
        adapter.verify(repo, 1.hours)
        val runsBefore = runs.size

        val text = adapter.apiChanges(repo, change, listOf(error))

        assertContains(text, "org.example.lib.Greeter.greetPolitely(java.lang.String) → org.example.lib.Greeter.greet(java.lang.String)")
        assertEquals(runsBefore, runs.size, "grounding starts no Gradle: ${runs.drop(runsBefore)}")
    }

    @Test
    fun `without a model that has the old version, there are no API changes`() {
        val (_, new) = jars()
        gradleProject()
        val adapter = adapter()
        query = model(new, "2.0")
        adapter.verify(repo, 1.hours)

        assertEquals("", adapter.apiChanges(repo, change, listOf(error)))
    }

    @Test
    fun `the base commit's included builds don't become the head's build files`() {
        val (old, new) = jars()
        gradleProject()
        gradleProject(dir = base)
        val adapter = adapter()
        query = model(old, "1.0", includedBuilds = """["old-logic"]""")
        adapter.resolvedVersions.read(base)
        query = model(new, "2.0")
        adapter.verify(repo, 1.hours)
        adapter.apiChanges(repo, change, listOf(error))

        assertFalse(adapter.isBuildFile("old-logic/src/main/kotlin/Conventions.kt"), "only the head's included builds are build files")
    }

    /** The old and the new JAR of `org.example:lib-core`: 2.0 renamed `greetPolitely` to `greet`. */
    private fun jars(): Pair<Path, Path> {
        val old = repo.resolve("cache/lib-core-1.0.jar")
        val new = repo.resolve("cache/lib-core-2.0.jar")
        buildJar(
            old,
            mapOf(
                "org.example.lib.Greeter" to
                    "package org.example.lib; public class Greeter { public String greetPolitely(String n) { return n; } }",
            ),
        )
        buildJar(
            new,
            mapOf(
                "org.example.lib.Greeter" to
                    "package org.example.lib; public class Greeter { public String greet(String n) { return n; } }",
            ),
        )
        return old to new
    }

    private fun model(
        jar: Path,
        version: String,
        includedBuilds: String = "[]",
    ) = """
        {"versions": {"org.example:lib-core": ["$version"]}, "plugins": [], "includedBuilds": $includedBuilds, "classpath": [
          {"module": "org.example:lib-core", "version": "$version", "file": "$jar"}
        ]}
        """.trimIndent()
}
