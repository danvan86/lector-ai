package gmbh.vanella.lector.recipes.generator

import gmbh.vanella.lector.recipes.IndexFormat
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.Path
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenerateRecipeIndexTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `exact versions and X-ranges are supported, other selectors are skipped with OpenRewrite's name for them`() {
        assertNull(skipReason("3.4.x", null))
        assertNull(skipReason("3.x", null))
        assertNull(skipReason("3.4.1", null))
        assertEquals("LatestRelease", skipReason("latest.release", null))
        assertEquals("LatestPatch", skipReason("latest.patch", null))
        assertEquals("SetRange", skipReason("[3.5.0,4.0.0)", null))
        assertEquals("HyphenRange", skipReason("1.0 - 2.0", null))
        assertEquals("ExactVersionWithPattern", skipReason("33.0.0", "-jre"))
        assertEquals("invalid selector", skipReason("not a version", null))
    }

    @Test
    fun `indexes the recipes of the OpenRewrite JARs it is given, and reports every one with its license`() {
        val mavenJar = classpathJar("rewrite-maven-")
        val version =
            mavenJar.fileName
                .toString()
                .removePrefix("rewrite-maven-")
                .removeSuffix(".jar")
        val noLicense = recipeJar("no-license.jar", Manifest().apply { mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0" })
        val noManifest = recipeJar("no-manifest.jar", manifest = null)
        val output = dir.resolve("out")

        main(
            arrayOf(
                output.toString(),
                "org.openrewrite.recipe:rewrite-recipe-bom:1.0",
                "$mavenJar=org.openrewrite:rewrite-maven:$version",
                "$noLicense=org.openrewrite:no-license:1.0",
                "$noManifest=org.openrewrite:no-manifest:1.0",
                "${classpathJar("slf4j-api-")}=org.slf4j:slf4j-api:2.0.0",
            ),
        )

        val entries = IndexFormat.read(output.resolve("recipe-index.json").readText())
        val report = output.resolve("recipe-index-report.md").readText()
        val (own, maven) = entries.partition { it.recipe == "com.example.UpgradeExample" }
        assertTrue(maven.isNotEmpty())
        assertTrue(maven.all { it.artifact == "org.openrewrite:rewrite-maven:$version" && it.license == "Apache License Version 2.0" })
        assertEquals(setOf("org.openrewrite:no-license:1.0", "org.openrewrite:no-manifest:1.0"), own.map { it.artifact }.toSet())
        assertTrue(own.all { it.packagePattern == "com.example:parent" && it.selector == "2.x" && it.license == "unknown" })
        assertContains(report, "Generated from `org.openrewrite.recipe:rewrite-recipe-bom:1.0`.")
        assertContains(report, "| org.openrewrite:rewrite-maven:$version | Apache License Version 2.0 |")
        assertContains(report, "| org.openrewrite:no-license:1.0 | unknown | 1 |")
        assertContains(report, "| org.openrewrite:no-manifest:1.0 | unknown | 1 |")
        assertTrue("slf4j" !in report, report)
    }

    private fun classpathJar(prefix: String): Path =
        System
            .getProperty("java.class.path")
            .split(File.pathSeparator)
            .map { Path(it) }
            .single { it.fileName.toString().startsWith(prefix) && it.toString().endsWith(".jar") }

    /** A JAR with one declarative recipe that upgrades a parent. */
    private fun recipeJar(
        name: String,
        manifest: Manifest?,
    ): Path {
        val jar = dir.resolve(name)
        val stream = if (manifest == null) JarOutputStream(jar.outputStream()) else JarOutputStream(jar.outputStream(), manifest)
        stream.use {
            it.putNextEntry(JarEntry("META-INF/rewrite/example.yml"))
            it.write(RECIPE.toByteArray())
        }
        return jar
    }

    private companion object {
        val RECIPE =
            """
            type: specs.openrewrite.org/v1beta/recipe
            name: com.example.UpgradeExample
            displayName: Upgrade the example parent
            recipeList:
              - org.openrewrite.maven.UpgradeParentVersion:
                  groupId: com.example
                  artifactId: parent
                  newVersion: 2.x
            """.trimIndent()
    }
}
