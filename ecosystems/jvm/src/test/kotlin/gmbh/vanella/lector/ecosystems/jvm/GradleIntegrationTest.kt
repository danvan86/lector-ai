package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.UpdateType
import gmbh.vanella.lector.core.runProcess
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The Gradle adapter with real Gradle on the fixture repos. Needs network access, so it is opt-in:
 * `./gradlew :ecosystems:jvm:integrationTest`. The task provides JDKs 21 and 25 and a Gradle user home of its own.
 */
@Tag("integration")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class GradleIntegrationTest {
    @TempDir
    lateinit var dir: Path

    private val jdks =
        System
            .getenv()
            .filterKeys { it.startsWith("LECTOR_JDK_") }
            .map { (name, home) -> name.removePrefix("LECTOR_JDK_").toInt() to Path.of(home) }
            .toMap()
    private val userHome = Path.of(checkNotNull(System.getenv("GRADLE_USER_HOME")) { "run with :ecosystems:jvm:integrationTest" })

    // Compiler messages in English, as in the tool image (LANG=C.UTF-8), whatever the machine's locale.
    private val adapter =
        GradleAdapter(
            jdks = jdks,
            environment = mapOf("GRADLE_USER_HOME" to userHome.toString()),
        ) { command, workDir, environment, timeout ->
            runProcess(command, workDir, timeout, environment + ("JAVA_TOOL_OPTIONS" to "-Duser.language=en -Duser.country=US"))
        }

    @AfterTest
    fun `stop the daemons`() = adapter.close()

    @Test
    fun `the query reads versions, plugin markers, and the compile classpath of a build that doesn't compile`() {
        val repo = fixture("gradle-kotlin-catalog")

        val versions = adapter.resolvedVersions.read(repo)
        val problems = adapter.verify(repo, 10.minutes)
        val change = DependencyChange("maven", "org.apache.commons:commons-lang3", "3.12.0", "3.18.0", UpdateType.MINOR)
        val grounding = adapter.ground(repo, change, problems)

        assertEquals("2.2.0", versions["org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin"])
        assertEquals("3.18.0", versions["org.apache.commons:commons-lang3"])
        assertEquals(listOf("kotlinc", "kotlinc"), problems.map { it.tool })
        assertContains(grounding, "public class org.apache.commons.lang3.StringUtils", message = "the classpath JARs exist")
    }

    @Test
    fun `stage 1 compiles every source set, the integration tests too, and stage 2 doesn't run`() {
        val repo = fixture("gradle-multi-project")
        val test = repo.resolve("app/src/integrationTest/java/com/example/app/GreetingIT.java")
        test.writeText(test.readText().replace("App.greeting()", "App.greet()"))

        val problems = adapter.verify(repo, 10.minutes)

        assertEquals(
            listOf(
                "app/src/integrationTest/java/com/example/app/GreetingIT.java" to 10,
                "app/src/integrationTest/java/com/example/app/GreetingIT.java" to 15,
            ),
            problems.map { it.file to it.line },
        )
        assertEquals(null, adapter.testsRun(repo), "no test ran")
    }

    @Test
    fun `stage 2 runs every Test task and nothing else of check, runTests reruns one task's tests, and included builds are build files`() {
        val repo = fixture("gradle-multi-project")

        val problems = adapter.verify(repo, 10.minutes)
        val testsRun = adapter.testsRun(repo)
        val rerun = adapter.runTests(repo, problems.filter { it.tool == ":app:integrationTest" }, 10.minutes)

        assertEquals(
            listOf(
                ":app:integrationTest" to "com.example.app.GreetingIT#greetsWrong()",
                ":lib:test" to "com.example.lib.CalculatorTest#addsWrong()",
            ),
            problems.map { it.tool to it.test },
        )
        assertFalse(problems.any { "lint" in it.message }, "the lint task bound to check never ran: $problems")
        assertEquals(4, testsRun)
        assertEquals(listOf("com.example.app.GreetingIT#greetsWrong()"), rerun.map { it.test })
        assertTrue(adapter.isBuildFile("conventions/src/main/kotlin/Conventions.kt"), "the included build's files are build files")
    }

    @Test
    fun `a toolchain that isn't installed is a tool error`() {
        val repo = fixture("gradle-groovy")
        val build = repo.resolve("build.gradle")
        build.writeText(build.readText().replace("JavaLanguageVersion.of(21)", "JavaLanguageVersion.of(11)"))

        val error = assertFailsWith<ToolError> { adapter.verify(repo, 10.minutes) }

        assertTrue(error.message.startsWith("The project asks for a Java 11 toolchain, but only JDKs"), error.message)
    }

    @Test
    fun `no daemon of the run is left after close, also after a failed build`() {
        val repo = fixture("gradle-groovy")

        val problems = adapter.verify(repo, 10.minutes)
        adapter.close()
        val status = runProcess(listOf(repo.resolve("gradlew").toString(), "--status"), repo, 2.minutes, gradleEnvironment())

        assertEquals("javac", problems.first().tool)
        assertEquals(0, status.exitCode, status.output)
        assertFalse(Regex("""\b(IDLE|BUSY)\b""").containsMatchIn(status.output), status.output)
    }

    /** A copy of the fixture with the tool's own wrapper. */
    private fun fixture(name: String): Path {
        val repo = dir.resolve(name)
        Path.of(checkNotNull(javaClass.getResource("/fixtures/$name")).toURI()).toFile().copyRecursively(repo.toFile())
        val wrapper = Path.of(checkNotNull(System.getProperty("lector.gradleWrapper")))
        wrapper.resolve("gradlew").copyTo(repo.resolve("gradlew"))
        wrapper
            .resolve(
                "gradle/wrapper/gradle-wrapper.jar",
            ).copyTo(repo.resolve("gradle/wrapper/gradle-wrapper.jar").createParentDirectories())
        repo.resolve("gradlew").toFile().setExecutable(true)
        return repo
    }

    // The JDK the fixture's Gradle 8.14 runs on, and the test's Gradle user home.
    private fun gradleEnvironment() = mapOf("JAVA_HOME" to jdks.getValue(21).toString(), "GRADLE_USER_HOME" to userHome.toString())
}
