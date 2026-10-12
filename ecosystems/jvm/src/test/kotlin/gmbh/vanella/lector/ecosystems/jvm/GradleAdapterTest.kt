package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.ProcessTimedOut
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.ToolError
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Detection, command lines, JDKs, verify, test runs, and the daemon. */
class GradleAdapterTest : FakeGradleTest() {
    @Test
    fun `speaks for the maven ecosystem, with Maven's version order`() {
        val adapter = adapter()

        assertEquals("maven", adapter.ecosystem)
        assertEquals(MavenVersionScheme, adapter.versionScheme)
    }

    @Test
    fun `detects a repository with any of the four root build files`() {
        for (file in listOf("settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts")) {
            val dir = repo.resolve(file.replace('.', '-')).createDirectories()
            gradleProject(dir, buildFile = file)

            assertTrue(adapter().detect(dir), file)
        }
    }

    @Test
    fun `doesn't detect a repository without root build files`() {
        repo.resolve("app/build.gradle.kts").createParentDirectories().writeText("")
        repo.resolve("pom.xml").writeText("<project/>")

        assertFalse(adapter().detect(repo))
    }

    @Test
    fun `a pom_xml next to Gradle build files is a tool error`() {
        gradleProject()
        repo.resolve("pom.xml").writeText("<project/>")

        val error = assertFailsWith<ToolError> { adapter().detect(repo) }

        assertEquals(
            "$repo has both a pom.xml and Gradle build files at the root. lector-ai doesn't pick a build tool: remove one of them.",
            error.message,
        )
    }

    @Test
    fun `a Gradle build without a wrapper is a tool error`() {
        gradleProject()
        repo.resolve("gradlew").deleteExisting()

        val error = assertFailsWith<ToolError> { adapter().detect(repo) }

        assertEquals(
            "$repo has no Gradle wrapper (gradlew). lector-ai runs Gradle only through the wrapper, which names the Gradle version.",
            error.message,
        )
    }

    @Test
    fun `a wrapper with Gradle 7 is a tool error`() {
        gradleProject(gradle = "7.6.4")

        val error = assertFailsWith<ToolError> { adapter().detect(repo) }

        assertEquals("The wrapper runs Gradle 7.6.4. lector-ai supports Gradle 8 and newer.", error.message)
    }

    @Test
    fun `a wrapper without a readable Gradle version is a tool error`() {
        gradleProject()
        wrapperProperties().writeText("distributionUrl=https\\://example.org/gradle.zip\n")

        val error = assertFailsWith<ToolError> { adapter().detect(repo) }

        assertEquals(
            "Can't read the Gradle version from distributionUrl in gradle/wrapper/gradle-wrapper.properties: https://example.org/gradle.zip",
            error.message,
        )
        wrapperProperties().writeText("distributionBase=GRADLE_USER_HOME\n")
        assertContains(assertFailsWith<ToolError> { adapter().detect(repo) }.message, "has no distributionUrl")
        wrapperProperties().deleteExisting()
        assertContains(assertFailsWith<ToolError> { adapter().detect(repo) }.message, "gradle-wrapper.properties is missing")
    }

    @Test
    fun `reads release candidates and versions with a patch number`() {
        for (gradle in listOf("8.14.3", "9.0.0-rc-1", "8.10")) {
            gradleProject(gradle = gradle)

            assertTrue(adapter().detect(repo), gradle)
        }
    }

    @Test
    fun `every Gradle run uses the wrapper, our init script, and the plain console`() {
        gradleProject()
        val adapter = adapter()

        adapter.verify(repo, 1.hours)
        adapter.resolvedVersions.read(repo)
        adapter.runTests(repo, listOf(testFailure(":test", "com.example.AppTest#adds")), 1.hours)

        assertEquals(5, runs.size)
        for (command in runs) {
            assertEquals(listOf(root.resolve("gradlew").toString(), "-I"), command.take(2))
            assertTrue(Path.of(command[2]).readText().contains("lectorQuery"), "our init script: ${command[2]}")
            assertEquals("--console=plain", command[3])
        }
    }

    @Test
    fun `the user's init script is passed to every run as a second init script`() {
        gradleProject()
        val init = repo.resolve("mirror.gradle")
        val adapter = adapter(init = init)

        adapter.verify(repo, 1.hours)
        adapter.runTests(repo, listOf(testFailure(":test", "com.example.AppTest#adds")), 1.hours)

        assertEquals(4, runs.size)
        runs.forEach { assertEquals(listOf("-I", init.toAbsolutePath().toString()), it.subList(3, 5)) }
    }

    @Test
    fun `toolchains come from the installed JDKs only, never from auto-detection or a download`() {
        gradleProject()
        val adapter = adapter(jdks = JDKS)

        adapter.verify(repo, 1.hours)

        for (command in runs) {
            assertContains(command, "-Dorg.gradle.java.installations.auto-detect=false")
            assertContains(command, "-Dorg.gradle.java.installations.auto-download=false")
            assertContains(command, "-Dorg.gradle.java.installations.paths=/opt/java/17,/opt/java/21,/opt/java/25")
        }
    }

    @Test
    fun `before Gradle 9_1, the toolchain settings are project properties`() {
        gradleProject(gradle = "9.0.0")

        adapter(jdks = JDKS).verify(repo, 1.hours)

        assertContains(runs.first(), "-Porg.gradle.java.installations.auto-detect=false")
        assertContains(runs.first(), "-Porg.gradle.java.installations.auto-download=false")
        assertContains(runs.first(), "-Porg.gradle.java.installations.paths=/opt/java/17,/opt/java/21,/opt/java/25")
    }

    @Test
    fun `without installed JDKs, Gradle runs on the environment's JAVA_HOME and still never downloads a JDK`() {
        gradleProject()

        adapter().verify(repo, 1.hours)

        assertEquals(listOf(emptyMap()), environments.distinct())
        assertContains(runs.first(), "-Dorg.gradle.java.installations.auto-download=false")
        assertFalse(runs.first().any { it.startsWith("-Dorg.gradle.java.installations.paths") })
    }

    @Test
    fun `Gradle runs on the highest installed JDK its version supports`() {
        val expected =
            mapOf(
                "8.0" to "/opt/java/17",
                "8.4" to "/opt/java/17",
                "8.5" to "/opt/java/21",
                "9.0.0" to "/opt/java/21",
                "9.1.0" to "/opt/java/25",
            )
        for ((gradle, javaHome) in expected) {
            gradleProject(gradle = gradle)
            environments.clear()

            adapter(jdks = JDKS).verify(repo, 1.hours)

            assertEquals(listOf(mapOf("JAVA_HOME" to javaHome)), environments.distinct(), gradle)
        }
    }

    @Test
    fun `the daemon JVM the project asks for wins over the table`() {
        gradleProject(gradle = "9.1.0")
        repo.resolve("gradle/gradle-daemon-jvm.properties").writeText("#This file is generated by updateDaemonJvm\ntoolchainVersion=17\n")

        adapter(jdks = JDKS).verify(repo, 1.hours)

        assertEquals(listOf(mapOf("JAVA_HOME" to "/opt/java/17")), environments.distinct())
    }

    @Test
    fun `daemon JVM properties without a Java version leave the choice to the table`() {
        gradleProject(gradle = "9.1.0")
        for (properties in listOf("toolchainVendor=adoptium\n", "toolchainVersion=latest\n")) {
            repo.resolve("gradle/gradle-daemon-jvm.properties").writeText(properties)
            environments.clear()

            adapter(jdks = JDKS).verify(repo, 1.hours)

            assertEquals(listOf(mapOf("JAVA_HOME" to "/opt/java/25")), environments.distinct(), properties)
        }
    }

    @Test
    fun `a Gradle version that runs on none of the installed JDKs is a tool error`() {
        gradleProject(gradle = "8.4")

        val error = assertFailsWith<ToolError> { adapter(jdks = mapOf(21 to Path.of("/opt/java/21"))).verify(repo, 1.hours) }

        assertEquals("Gradle 8.4 runs on Java 20 or older, but only JDKs 21 are installed.", error.message)
    }

    @Test
    fun `a daemon JVM that isn't installed is a tool error`() {
        gradleProject(gradle = "9.1.0")
        repo.resolve("gradle/gradle-daemon-jvm.properties").writeText("toolchainVersion=11\n")

        val error = assertFailsWith<ToolError> { adapter(jdks = JDKS).verify(repo, 1.hours) }

        assertEquals(
            "The project runs Gradle on Java 11 (gradle/gradle-daemon-jvm.properties), but only JDKs 17, 21, 25 are installed.",
            error.message,
        )
        assertEquals(emptyList(), runs)
    }

    @Test
    fun `Gradle's own variables are passed to every run, for the user home and credentials, and no others`() {
        gradleProject()
        val environment =
            mapOf(
                "GRADLE_USER_HOME" to "/github/home/.gradle",
                "GRADLE_OPTS" to "-Xmx2g",
                "ORG_GRADLE_PROJECT_mirrorPassword" to "secret",
                "LECTOR_GITHUB_TOKEN" to "token",
                "INPUT_TOKEN" to "token",
            )

        adapter(environment = environment).verify(repo, 1.hours)

        assertEquals(listOf(environment - "LECTOR_GITHUB_TOKEN" - "INPUT_TOKEN"), environments.distinct())
    }

    @Test
    fun `verify reads the model, compiles every source set, then runs every Test task, with --continue and the project's cache settings`() {
        gradleProject()

        adapter().verify(repo, 1.hours)

        assertEquals(listOf("lectorQuery", "lectorCompile", "lectorTest"), runs.map { task(it) })
        assertContains(runs[0], "--no-configuration-cache")
        for (stage in runs.drop(1)) {
            assertContains(stage, "--continue")
            assertFalse("--no-configuration-cache" in stage)
            assertFalse(stage.any { it == "check" || it == "build" || it.endsWith(":check") || it.endsWith(":build") }, "$stage")
        }
    }

    @Test
    fun `doesn't run the tests when the code doesn't compile`() {
        gradleProject()
        compileOutput = ProcessResult(1, captured("javac-errors.log"))

        val problems = adapter().verify(repo, 1.hours)

        assertEquals(listOf("lectorQuery", "lectorCompile"), runs.map { task(it) })
        assertEquals(listOf("javac"), problems.map { it.tool }.distinct())
    }

    @Test
    fun `a failed build without an error the parser knows is never green`() {
        gradleProject()
        compileOutput = ProcessResult(1, "Something went wrong\n")

        val problem = adapter().verify(repo, 1.hours).single()

        assertEquals("gradle", problem.tool)
        assertEquals("Gradle failed with exit code 1. The end of its output:\nSomething went wrong", problem.message)
    }

    @Test
    fun `the tests run anew on every verify, so no report of an earlier run is read`() {
        gradleProject()
        val stale = writeFailingReport("app", "integrationTest", "com.example.AppIT")

        adapter().verify(repo, 1.hours)

        assertFalse(stale.exists(), "$stale is removed before the tests run")
    }

    @Test
    fun `reports the test failures of every Test task, named by the task`() {
        gradleProject()
        testOutput = ProcessResult(1, "FAILURE: Build completed with 2 failures.\n")
        val adapter =
            adapter { command ->
                if (task(command) == "lectorTest") {
                    writeFailingReport("app", "integrationTest", "com.example.AppIT")
                    writeFailingReport("", "test", "com.example.LibTest")
                }
            }

        val problems = adapter.verify(repo, 1.hours)

        assertEquals(
            listOf(":app:integrationTest" to "com.example.AppIT#breaks", ":test" to "com.example.LibTest#breaks"),
            problems.map { it.tool to it.test },
        )
        assertEquals(2, adapter.testsRun(repo))
    }

    @Test
    fun `a toolchain that isn't installed is a tool error that names the Java version`() {
        gradleProject()
        compileOutput = ProcessResult(1, captured("toolchain-missing.log"))

        val error = assertFailsWith<ToolError> { adapter(jdks = JDKS).verify(repo, 1.hours) }

        assertEquals("The project asks for a Java 11 toolchain, but only JDKs 17, 21, 25 are installed.", error.message)
        val withoutJdks = assertFailsWith<ToolError> { adapter().verify(repo, 1.hours) }
        assertEquals("The project asks for a Java 11 toolchain, but none are installed.", withoutJdks.message)
    }

    @Test
    fun `cluster keys are the shared JVM ones`() {
        val error = Diagnostic("A.java", 1, 1, null, "cannot find symbol\nsymbol:   method greet()", Severity.ERROR, "javac")

        assertEquals("symbol:greet", adapter().clusterKey(error))
    }

    @Test
    fun `runs the wrapper as a process in the repository`() {
        gradleProject()
        val wrapper = repo.resolve("gradlew")
        wrapper.writeText("#!/bin/sh\necho \"$(pwd)\" > ran-in\nexit 0\n")
        wrapper.toFile().setExecutable(true)

        val problems =
            GradleAdapter().also { adapters += it }.runTests(
                repo,
                listOf(testFailure(":test", "com.example.AppTest#adds")),
                1.minutes,
            )

        assertEquals(emptyList(), problems)
        assertEquals(root.toString(), repo.resolve("ran-in").readText().trim())
    }

    @Test
    fun `a daemon that doesn't stop is logged, not a failure`() {
        gradleProject()
        val adapter =
            GradleAdapter { command, _, _, timeout ->
                when {
                    "--stop" in command && timeout == 1.minutes -> throw ProcessTimedOut("gradlew did not finish within 1m")
                    else -> ProcessResult(0, "")
                }
            }
        adapter.runTests(repo, listOf(testFailure(":test", "com.example.AppTest#adds")), 1.hours)
        adapter.close()

        val failing = GradleAdapter { command, _, _, _ -> ProcessResult(if ("--stop" in command) 1 else 0, "") }
        failing.runTests(repo, listOf(testFailure(":test", "com.example.AppTest#adds")), 1.hours)
        failing.close()
    }

    @Test
    fun `runs only the given tests, each in the Test task it came from`() {
        gradleProject()

        adapter().runTests(
            repo,
            listOf(
                testFailure(":app:integrationTest", "com.example.AppIT#breaks"),
                testFailure(":app:integrationTest", "com.example.AppIT#alsoBreaks"),
                testFailure(":test", "com.example.LibTest#breaks"),
            ),
            1.hours,
        )

        val command = runs.single()
        val tasks = command.dropWhile { it != "-Plector.runTests" }.drop(1)
        assertEquals(
            listOf(":app:integrationTest", "--tests", "com.example.AppIT", ":test", "--tests", "com.example.LibTest", "--continue"),
            tasks,
        )
        assertFalse("--no-configuration-cache" in command)
    }

    @Test
    fun `runTests returns the failures of its run and removes earlier reports first`() {
        gradleProject()
        val stale = writeFailingReport("lib", "test", "com.example.LibTest")
        val adapter =
            adapter { command ->
                if (command.contains("--tests")) writeFailingReport("app", "integrationTest", "com.example.AppIT")
            }

        val problems = adapter.runTests(repo, listOf(testFailure(":app:integrationTest", "com.example.AppIT#breaks")), 1.hours)

        assertEquals(listOf("com.example.AppIT#breaks"), problems.map { it.test })
        assertFalse(stale.exists())
    }

    @Test
    fun `builds and test runs get the given timeout, never more than an hour`() {
        gradleProject()
        val timeouts = mutableListOf<Duration>()
        val adapter = GradleAdapter { _, _, _, timeout -> ProcessResult(0, "").also { timeouts += timeout } }.also { adapters += it }

        adapter.runTests(repo, listOf(testFailure(":test", "com.example.AppTest#adds")), 5.minutes)
        adapter.runTests(repo, listOf(testFailure(":test", "com.example.AppTest#adds")), 3.hours)

        assertEquals(listOf(5.minutes, 1.hours), timeouts)
    }

    @Test
    fun `closing stops the daemons of every repository Gradle ran in, also after a failed run`() {
        gradleProject()
        val other = repo.resolve("other").createDirectories()
        gradleProject(other)
        val adapter = adapter()
        compileOutput = ProcessResult(1, captured("javac-errors.log"))

        adapter.verify(repo, 1.hours)
        adapter.verify(other, 1.hours)
        runs.clear()
        adapter.close()

        assertEquals(listOf(root.resolve("gradlew").toString(), other.toRealPath().resolve("gradlew").toString()), runs.map { it.first() })
        runs.forEach { assertEquals("--stop", it.last()) }
        runs.clear()
        adapter.close()
        assertEquals(emptyList(), runs, "a second close has nothing left to stop")
    }

    @Test
    fun `closing skips repositories that are gone, like a removed base worktree, and removes the init script`() {
        gradleProject()
        val adapter = adapter()
        adapter.verify(repo, 1.hours)
        val init = Path.of(runs.first()[2])
        repo.resolve("gradlew").deleteExisting()
        runs.clear()

        adapter.close()

        assertEquals(emptyList(), runs)
        assertFalse(init.exists())
    }
}
