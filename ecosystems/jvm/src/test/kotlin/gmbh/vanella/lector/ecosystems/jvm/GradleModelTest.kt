package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.Recipe
import gmbh.vanella.lector.core.RecipeRun
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.UpdateType
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** The query task's model, grounding, unsupported builds, test reports, and recipes. */
class GradleModelTest : FakeGradleTest() {
    @Test
    fun `without the OpenRewrite plugin there is no recipe runner`() {
        assertNull(adapter().recipeRunner)
    }

    @Test
    fun `runs a recipe with rewriteRun, applied by a second init script that loads the shipped plugin`() {
        gradleProject()
        val plugin = repo.resolve("plugins/it's here")
        val adapter = adapter(rewritePlugin = plugin)

        val run =
            assertNotNull(
                adapter.recipeRunner,
            ).run(repo, Recipe("org.example.UpgradeBoot", "org.example:recipes:1.2.3", "Apache-2.0"))

        assertEquals(RecipeRun.Applied, run)
        val command = runs.single()
        val arguments = command.drop(command.indexOf("--console=plain") + 1).filterNot { it.contains("org.gradle.java.installations") }
        assertEquals(
            listOf(
                "-I",
                arguments[1],
                "-Plector.recipe=org.example.UpgradeBoot",
                "-Plector.recipeArtifact=org.example:recipes:1.2.3",
                "rewriteRun",
                "--no-configuration-cache",
            ),
            arguments,
        )
        val script = Path.of(arguments[1]).readText()
        assertContains(script, "new File('${plugin.toAbsolutePath().toString().replace("'", "\\'")}')")
        assertContains(script, "setDependsOn([])", message = "rewriteRun doesn't compile first (M7 Q19)")
    }

    @Test
    fun `a recipe run that fails reports the end of Gradle's output`() {
        gradleProject()
        testOutput = ProcessResult(1, "* What went wrong:\nTask 'rewriteRun' not found in root project.\n")

        val run =
            assertNotNull(
                adapter(rewritePlugin = repo).recipeRunner,
            ).run(repo, Recipe("a.B", "org.example:recipes:1.0", "Apache-2.0"))

        assertEquals(
            RecipeRun.Failed(
                "Gradle failed with exit code 1. The end of its output:\n* What went wrong:\nTask 'rewriteRun' not found in root project.",
            ),
            run,
        )
    }

    @Test
    fun `an Android or Kotlin Multiplatform project is a tool error`() {
        gradleProject()
        for (plugin in listOf("com.android.application", "com.android.library", "org.jetbrains.kotlin.multiplatform")) {
            query = """{"versions": {}, "classpath": [], "plugins": ["java", "$plugin"], "includedBuilds": []}"""

            val error = assertFailsWith<ToolError> { adapter().verify(repo, 1.hours) }

            assertEquals(
                "The project applies the Gradle plugin $plugin. lector-ai doesn't support Android or Kotlin Multiplatform builds.",
                error.message,
            )
        }
    }

    @Test
    fun `a model Gradle can't read doesn't stop verify, which then reports why`() {
        gradleProject()
        queryOutput = ProcessResult(1, captured("groovy-script-error.log"))
        compileOutput = queryOutput!!

        val problems = adapter().verify(repo, 1.hours)

        assertEquals(listOf("lectorQuery", "lectorCompile"), runs.map { task(it) })
        assertContains(problems.single().message, "Could not set unknown property 'undefinedSetting'")
    }

    @Test
    fun `resolved versions are the model's, and a model Gradle can't read is a tool error with the end of its output`() {
        gradleProject()
        query = """{"versions": {"org.example:lib": ["1.2.0", "1.10.0", "1.9.0"]}, "classpath": [], "plugins": [], "includedBuilds": []}"""

        assertEquals(mapOf("org.example:lib" to "1.10.0"), adapter().resolvedVersions.read(repo))

        queryOutput = ProcessResult(1, captured("groovy-script-error.log"))
        val error = assertFailsWith<ToolError> { adapter().resolvedVersions.read(repo) }
        assertContains(error.message, "Gradle failed with exit code 1. The end of its output:")
        assertContains(error.message, "Could not set unknown property 'undefinedSetting'")
    }

    @Test
    fun `grounding reads the JARs of the change's group at its new version from the compile classpath of the last verify`() {
        gradleProject()
        val core = repo.resolve("cache/lib-core-2.0.jar")
        val old = repo.resolve("cache/lib-core-1.0.jar")
        buildJar(
            core,
            mapOf(
                "org.example.lib.Greeter" to
                    "package org.example.lib; public class Greeter { public String greet(String n) { return n; } }",
            ),
        )
        buildJar(
            old,
            mapOf("org.example.lib.Greeter" to "package org.example.lib; public class Greeter { public String hello() { return \"\"; } }"),
        )
        query =
            """
            {"versions": {}, "plugins": [], "includedBuilds": [], "classpath": [
              {"module": "org.example:lib-core", "version": "2.0", "file": "$core"},
              {"module": "org.example:lib-core", "version": "1.0", "file": "$old"},
              {"module": "org.example:gone", "version": "2.0", "file": "${repo.resolve("cache/gone.jar")}"},
              {"module": "org.other:lib-core", "version": "2.0", "file": "$old"}
            ]}
            """.trimIndent()
        val adapter = adapter()
        val change = DependencyChange("maven", "org.example:lib-core", "1.0", "2.0", UpdateType.MAJOR)
        val error =
            Diagnostic(
                "src/main/java/App.java",
                3,
                9,
                null,
                "cannot find symbol\nsymbol:   method hello()\nlocation: class org.example.lib.Greeter",
                Severity.ERROR,
                "javac",
            )

        assertEquals("", adapter.ground(repo, change, listOf(error)), "no model read yet")
        adapter.verify(repo, 1.hours)
        val text = adapter.ground(repo, change, listOf(error))

        assertTrue(text.startsWith("javap -public of org.example:lib-core 2.0:"), text)
        assertContains(text, "public java.lang.String greet(java.lang.String);")
        assertFalse("hello()" in text, "the old version's API is not shown: $text")
    }

    @Test
    fun `the files of the included builds are build files once verify has read the model`() {
        gradleProject()
        query = """{"versions": {}, "classpath": [], "plugins": [], "includedBuilds": ["conventions", "gradle/plugins"]}"""
        val adapter = adapter()
        val plugin = "conventions/src/main/kotlin/JavaConventions.kt"

        assertFalse(adapter.isBuildFile(plugin), "no model read yet")
        adapter.verify(repo, 1.hours)

        assertTrue(adapter.isBuildFile(plugin))
        assertTrue(adapter.isBuildFile("gradle/plugins/src/main/kotlin/Publishing.kt"))
        assertFalse(adapter.isBuildFile("conventions-app/src/main/kotlin/App.kt"))
        assertFalse(adapter.isBuildFile("src/main/kotlin/conventions/App.kt"))
        assertTrue(adapter.isEditable(plugin), "build files stay editable")
    }

    @Test
    fun `the query's output that isn't the model's JSON is a tool error`() {
        gradleProject()
        for (json in listOf("not json", """{"versions": {}}""", """{"versions": {}, "classpath": [], "plugins": []}""", "[]")) {
            query = json

            val error = assertFailsWith<ToolError> { adapter().resolvedVersions.read(repo) }

            assertTrue(error.message.startsWith("Can't read the model of the Gradle build: "), error.message)
        }
    }

    @Test
    fun `detects the fixture repos for the Groovy DSL, the Kotlin DSL with a version catalog, and a multi-project build`() {
        for (fixture in listOf("gradle-groovy", "gradle-kotlin-catalog", "gradle-multi-project")) {
            val copy = repo.resolve(fixture)
            Path.of(checkNotNull(javaClass.getResource("/fixtures/$fixture")).toURI()).toFile().copyRecursively(copy.toFile())
            copy.resolve("gradlew").writeText("#!/bin/sh\n")

            assertTrue(adapter().detect(copy), fixture)
        }
    }

    @Test
    fun `reads the captured reports of the multi-project fixture, located in the test sources`() {
        val fixture = Path.of(checkNotNull(javaClass.getResource("/fixtures/gradle-multi-project")).toURI())
        fixture.toFile().copyRecursively(repo.toFile())
        val reports = Path.of(checkNotNull(javaClass.getResource("/gradle-output/test-results")).toURI())
        for (project in listOf("app", "lib")) {
            reports.resolve(project).toFile().copyRecursively(repo.resolve("$project/build/test-results").toFile())
        }

        val failures = GRADLE_REPORTS.parse(root)

        assertEquals(
            listOf(
                Diagnostic(
                    "app/src/integrationTest/java/com/example/app/GreetingIT.java",
                    15,
                    null,
                    "org.opentest4j.AssertionFailedError",
                    "org.opentest4j.AssertionFailedError: expected: <1 + 1 = 3> but was: <1 + 1 = 2>",
                    Severity.ERROR,
                    ":app:integrationTest",
                    "com.example.app.GreetingIT#greetsWrong()",
                ),
                Diagnostic(
                    "lib/src/test/java/com/example/lib/CalculatorTest.java",
                    15,
                    null,
                    "org.opentest4j.AssertionFailedError",
                    "org.opentest4j.AssertionFailedError: expected: <4> but was: <3>",
                    Severity.ERROR,
                    ":lib:test",
                    "com.example.lib.CalculatorTest#addsWrong()",
                ),
            ),
            failures,
        )
        assertEquals(4, GRADLE_REPORTS.count(root))
    }

    @Test
    fun `a report that can't be read is a Gradle problem`() {
        repo.resolve("build/test-results/test/TEST-com.example.AppTest.xml").createParentDirectories().writeText("<testsuite")

        assertEquals("gradle", GRADLE_REPORTS.parse(root).single().tool)
    }

    @Test
    fun `the init scripts ship as resources, and a missing one says so`() {
        assertContains(resourceText("lector.init.gradle"), "lectorQuery")
        assertEquals(
            "missing.gradle is missing from the classpath",
            assertFailsWith<IllegalStateException> {
                resourceText("missing.gradle")
            }.message,
        )
    }
}
