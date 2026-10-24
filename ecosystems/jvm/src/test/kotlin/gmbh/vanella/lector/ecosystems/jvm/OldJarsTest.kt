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
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Where the old version's JARs come from on a CI runner (M10): a fresh local repository has only the new version, so
 * Maven fetches the old one, once, through the project's settings, and never into the user's repository. Any failure
 * means no API changes, never a failed run.
 */
class OldJarsTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var localRepository: Path

    @TempDir
    lateinit var built: Path

    private val core = DependencyChange("maven", "org.example:lib-core", "1.0", "2.0", UpdateType.MAJOR)
    private val extra = DependencyChange("maven", "org.example:lib-extra", "1.0", "2.0", UpdateType.MAJOR)
    private val app = "src/main/java/com/example/App.java"
    private val settings by lazy { repo.resolve(".mvn/ci-settings.xml").createParentDirectories().apply { writeText("<settings/>") } }

    private val calls = mutableListOf<List<String>>()
    private val timeouts = mutableListOf<Duration>()

    // How the fake Maven answers a fetch: it places the old JAR where a real fetch would.
    private var answer: (List<String>) -> ProcessResult = { command -> fetched(command) }

    private val adapter by lazy {
        MavenAdapter(settings = settings, localRepository = localRepository) { command, _, _, timeout ->
            calls += command
            timeouts += timeout
            answer(command)
        }
    }

    @BeforeTest
    fun `the new version is resolved, the old one only exists in the remote repository`() {
        buildJar(localRepository.resolve("org/example/lib-core/2.0/lib-core-2.0.jar"), mapOf("org.example.lib.Greeter" to greeter("greet")))
        buildJar(localRepository.resolve("org/example/lib-extra/2.0/lib-extra-2.0.jar"), mapOf("org.example.extra.Strings" to strings))
        buildJar(built.resolve("lib-core-1.0.jar"), mapOf("org.example.lib.Greeter" to greeter("greetPolitely")))
        buildJar(built.resolve("lib-extra-1.0.jar"), mapOf("org.example.extra.Strings" to strings))
        repo.resolve(app).createParentDirectories().writeText("package com.example;\n\nimport org.example.lib.Greeter;\n")
    }

    @Test
    fun `old artifacts are fetched once per group and version, one call each, with the run's settings, never into the repository`() {
        // After the setup: the adapter's settings file is the test's own, not a write of the fetch.
        val before = adapter.let { files(repo) }

        val first = adapter.apiChanges(repo, core, listOf(error()))
        adapter.apiChanges(repo, core, listOf(error()))
        adapter.apiChanges(repo, extra, listOf(error()))

        assertContains(first, "org.example.lib.Greeter.greetPolitely(java.lang.String) → org.example.lib.Greeter.greet(java.lang.String)")
        assertEquals(2, calls.size, "one fetch per artifact of the group at 1.0, shared by both changes and every attempt: $calls")
        assertTrue(calls.any { command -> command.any { "org.example:lib-core:1.0" in it } }, "$calls")
        assertTrue(calls.any { command -> command.any { "org.example:lib-extra:1.0" in it } }, "$calls")
        calls.forEach { command ->
            assertTrue(command.any { "maven-dependency-plugin" in it }, "fetched with the dependency plugin: $command")
            assertTrue("-N" in command, "not once per module: $command")
            assertTrue(settings.toString() in command, "through the run's settings: $command")
        }
        assertEquals(listOf(2.minutes, 2.minutes), timeouts)
        assertEquals(before, files(repo), "nothing is written into the user's repository")
    }

    @Test
    fun `a failed fetch gives no API changes and is not retried on the next attempt`() {
        answer = { ProcessResult(1, "[ERROR] Failed to execute goal: Could not find artifact org.example:lib-core:jar:1.0") }

        assertEquals("", adapter.apiChanges(repo, core, listOf(error())))
        val afterFirst = calls.size
        assertEquals("", adapter.apiChanges(repo, core, listOf(error())))

        assertTrue(afterFirst > 0)
        assertEquals(afterFirst, calls.size, "the failure is remembered for the run")
    }

    @Test
    fun `a fetch that runs into its timeout gives no API changes and doesn't fail the run`() {
        answer = { throw ProcessTimedOut("Maven ran longer than 2m") }

        assertEquals("", adapter.apiChanges(repo, core, listOf(error())))
    }

    @Test
    fun `an old JAR that can't be read gives no API changes`() {
        localRepository.resolve("org/example/lib-core/1.0/lib-core-1.0.jar").createParentDirectories().writeText("not a zip")
        localRepository.resolve("org/example/lib-extra/1.0/lib-extra-1.0.jar").createParentDirectories().writeText("not a zip")
        answer = { ProcessResult(1, "[ERROR] unexpected fetch") }

        assertEquals("", adapter.apiChanges(repo, core, listOf(error())))
    }

    @Test
    fun `nothing is fetched for errors the new version's classes have nothing to do with`() {
        val unrelated =
            Diagnostic(app, 3, 8, null, "cannot find symbol\nsymbol:   class Lisst\nlocation: package java.util", Severity.ERROR, "javac")

        assertEquals("", adapter.apiChanges(repo, core, listOf(unrelated)))
        assertTrue(calls.isEmpty(), "$calls")
    }

    @Test
    fun `nothing is fetched for a package that was added, removed, or not changed`() {
        assertEquals("", adapter.apiChanges(repo, core.copy(fromVersion = ""), listOf(error())))
        assertEquals("", adapter.apiChanges(repo, core.copy(toVersion = ""), listOf(error())))
        assertEquals("", adapter.apiChanges(repo, core.copy(fromVersion = "2.0"), listOf(error())))

        assertTrue(calls.isEmpty(), "$calls")
    }

    /** Puts the old JAR of the artifact [command] names where Maven would: the local repository, or its output directory. */
    private fun fetched(command: List<String>): ProcessResult {
        val artifact = listOf("lib-core", "lib-extra").single { name -> command.any { "org.example:$name:1.0" in it } }
        built
            .resolve(
                "$artifact-1.0.jar",
            ).copyTo(localRepository.resolve("org/example/$artifact/1.0/$artifact-1.0.jar").createParentDirectories())
        command.firstOrNull { it.startsWith("-DoutputDirectory=") }?.let { option ->
            val dir = Path.of(option.substringAfter('='))
            built.resolve("$artifact-1.0.jar").copyTo(dir.resolve("$artifact-1.0.jar").createParentDirectories())
        }
        return ProcessResult(0, "[INFO] BUILD SUCCESS")
    }

    private fun files(dir: Path) =
        dir
            .walk()
            .filter { it.isRegularFile() }
            .map { dir.relativize(it).invariantSeparatorsPathString }
            .toSet()

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

    private val strings = "package org.example.extra; public class Strings { public static String trim(String s) { return s; } }"
}
