package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.Recipe
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.UpdateType
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** M11 section 3: no Maven build lector runs signs artifacts. Signing is never what an update breaks. */
class MavenSigningTest {
    @TempDir
    lateinit var repo: Path

    private val runs = mutableListOf<List<String>>()

    @Test
    fun `every Maven command skips signing`() {
        val adapter =
            MavenAdapter(plugins = PLUGINS) { command, _, _, _ ->
                runs += command
                command.firstOrNull { it.startsWith("-Doutput=") }?.let {
                    Path.of(it.removePrefix("-Doutput=")).writeText("<projects/>")
                }
                ProcessResult(0, "")
            }

        adapter.verify(repo, 1.hours)
        adapter.runTests(
            repo,
            listOf(Diagnostic(null, null, null, null, "failed", Severity.ERROR, "surefire", "com.example.FooTest#bar")),
            1.hours,
        )
        adapter.recipeRunner.run(repo, Recipe("org.example.UpgradeBoot", "org.example:recipes:1.2.3", "Apache License 2.0"))
        adapter.resolvedVersions.read(repo)

        assertEquals(5, runs.size, "compile, verify, the rerun, the recipe, the effective POM")
        runs.forEach { assertTrue("-Dgpg.skip=true" in it, "skips signing: $it") }
    }

    @TempDir
    lateinit var localRepository: Path

    @TempDir
    lateinit var built: Path

    @Test
    fun `fetching the old version's JAR for the API changes skips signing too`() {
        fun greeter(method: String) = "package org.example.lib; public class Greeter { public String $method(String n) { return n; } }"
        buildJar(
            localRepository.resolve("org/example/lib-core/2.0/lib-core-2.0.jar"),
            mapOf("org.example.lib.Greeter" to greeter("greet")),
        )
        buildJar(built.resolve("lib-core-1.0.jar"), mapOf("org.example.lib.Greeter" to greeter("greetPolitely")))
        val app = "src/main/java/com/example/App.java"
        repo.resolve(app).createParentDirectories().writeText("package com.example;\n\nimport org.example.lib.Greeter;\n")
        val adapter =
            MavenAdapter(plugins = PLUGINS, localRepository = localRepository) { command, _, _, _ ->
                runs += command
                built
                    .resolve(
                        "lib-core-1.0.jar",
                    ).copyTo(localRepository.resolve("org/example/lib-core/1.0/lib-core-1.0.jar").createParentDirectories())
                ProcessResult(0, "[INFO] BUILD SUCCESS")
            }
        val error =
            Diagnostic(
                app,
                3,
                8,
                null,
                "cannot find symbol\nsymbol:   method greetPolitely(java.lang.String)\nlocation: variable g of type org.example.lib.Greeter",
                Severity.ERROR,
                "javac",
            )

        val changes =
            adapter.apiChanges(
                repo,
                DependencyChange("maven", "org.example:lib-core", "1.0", "2.0", UpdateType.MAJOR),
                listOf(error),
            )

        assertContains(changes, "greetPolitely", message = "the old JAR was fetched")
        assertTrue(runs.isNotEmpty())
        runs.forEach { assertTrue("-Dgpg.skip=true" in it, "skips signing: $it") }
    }

    private companion object {
        val PLUGINS =
            MavenPlugins(
                "org.openrewrite.maven:rewrite-maven-plugin:9.9.9",
                "org.apache.maven.plugins:maven-help-plugin:8.8.8",
                "org.apache.maven.plugins:maven-dependency-plugin:7.7.7",
            )
    }
}
