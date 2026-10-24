package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.ProcessTimedOut
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.UpdateType
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Grounding that can't read what it needs gives no grounding, never an exception that would end the run. */
class GroundingFailuresTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var localRepository: Path

    private val change = DependencyChange("maven", "org.example:lib-core", "1.0", "2.0", UpdateType.MAJOR)
    private val app = "src/main/java/com/example/App.java"
    private val error =
        Diagnostic(
            app,
            3,
            8,
            null,
            "cannot find symbol\nsymbol:   method greetPolitely(java.lang.String)\nlocation: variable g of type org.example.lib.Greeter",
            Severity.ERROR,
            "javac",
        )

    @BeforeTest
    fun `the code imports a class of the library`() {
        repo.resolve(app).createParentDirectories().writeText("package com.example;\n\nimport org.example.lib.Greeter;\n")
    }

    private fun greeter(method: String) =
        mapOf("org.example.lib.Greeter" to "package org.example.lib; public class Greeter { public void $method(String n) {} }")

    @Test
    fun `a class file that can't be parsed gives no API changes`() {
        buildJar(localRepository.resolve("org/example/lib-core/2.0/lib-core-2.0.jar"), greeter("greet"))
        JarOutputStream(localRepository.resolve("org/example/lib-core/1.0/lib-core-1.0.jar").createParentDirectories().outputStream()).use {
            it.putNextEntry(ZipEntry("org/example/lib/Greeter.class"))
            it.write(byteArrayOf(1, 2, 3))
            it.closeEntry()
        }

        assertEquals("", MavenAdapter(localRepository = localRepository).apiChanges(repo, change, listOf(error)))
    }

    @Test
    fun `a new JAR that can't be read gives no API changes and no full API`() {
        localRepository.resolve("org/example/lib-core/2.0/lib-core-2.0.jar").createParentDirectories().writeText("not a zip")
        val adapter = MavenAdapter(localRepository = localRepository)

        assertEquals("", adapter.apiChanges(repo, change, listOf(error)))
        assertEquals("", adapter.ground(repo, change, listOf(error)))
    }

    @Test
    fun `a javap that runs into its timeout gives no full API`() {
        buildJar(localRepository.resolve("org/example/lib-core/2.0/lib-core-2.0.jar"), greeter("greet"))
        val grounding = JavapGrounding(localRepositoryJars(localRepository)) { _, _ -> throw ProcessTimedOut("javap ran longer than 2m") }

        assertEquals("", grounding.ground(repo, change, listOf(error)))
    }
}
