package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.ProcessTimedOut
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.UpdateType
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * The order of the fetches for the old version: a major release often adds artifacts (PDFBox 3 added `pdfbox-io`) that
 * have no old version. Their failed fetch skips only them; a timeout ends the lookup. The change's own artifact comes first.
 */
class OldJarsOrderTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var localRepository: Path

    @TempDir
    lateinit var built: Path

    private val change = DependencyChange("maven", "org.example:lib-core", "1.0", "2.0", UpdateType.MAJOR)
    private val app = "src/main/java/com/example/App.java"
    private val fetched = mutableListOf<String>()

    private fun adapter(answer: (String) -> ProcessResult) =
        MavenAdapter(localRepository = localRepository) { command, _, _, _ ->
            val artifact = command.single { it.startsWith("-Dartifact=") }.removePrefix("-Dartifact=org.example:").substringBefore(':')
            fetched += artifact
            answer(artifact)
        }

    @BeforeTest
    fun `the new version has a new artifact that sorts first`() {
        buildJar(
            localRepository.resolve("org/example/a-new-io/2.0/a-new-io-2.0.jar"),
            mapOf("org.example.io.Io" to "package org.example.io; public class Io {}"),
        )
        buildJar(localRepository.resolve("org/example/lib-core/2.0/lib-core-2.0.jar"), mapOf("org.example.lib.Greeter" to greeter("greet")))
        buildJar(built.resolve("lib-core-1.0.jar"), mapOf("org.example.lib.Greeter" to greeter("greetPolitely")))
        repo.resolve(app).createParentDirectories().writeText("package com.example;\n\nimport org.example.lib.Greeter;\n")
    }

    @Test
    fun `an artifact the old version doesn't have skips only itself`() {
        val adapter =
            adapter { artifact ->
                if (artifact == "lib-core") {
                    built
                        .resolve(
                            "lib-core-1.0.jar",
                        ).copyTo(localRepository.resolve("org/example/lib-core/1.0/lib-core-1.0.jar").createParentDirectories())
                    ProcessResult(0, "[INFO] BUILD SUCCESS")
                } else {
                    ProcessResult(1, "[ERROR] Could not find artifact org.example:a-new-io:jar:1.0 in central")
                }
            }

        val text = adapter.apiChanges(repo, change, listOf(error()))

        assertContains(text, "org.example.lib.Greeter.greetPolitely(java.lang.String) → org.example.lib.Greeter.greet(java.lang.String)")
        assertEquals(listOf("lib-core", "a-new-io"), fetched, "the change's own artifact first")
    }

    @Test
    fun `a fetch that runs into its timeout ends the lookup`() {
        val adapter = adapter { throw ProcessTimedOut("Maven ran longer than 2m") }

        assertEquals("", adapter.apiChanges(repo, change, listOf(error())))
        assertEquals(listOf("lib-core"), fetched)
    }

    private fun error() =
        Diagnostic(
            app,
            3,
            8,
            null,
            "cannot find symbol\nsymbol:   method greetPolitely(java.lang.String)\nlocation: variable g of type org.example.lib.Greeter",
            Severity.ERROR,
            "javac",
        )

    private fun greeter(method: String) = "package org.example.lib; public class Greeter { public String $method(String n) { return n; } }"
}
