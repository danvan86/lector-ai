package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.Severity
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.assertEquals

/** A fake Gradle that answers like its fields say, and the repositories it builds, for the Gradle adapter's tests. */
open class FakeGradleTest {
    @TempDir
    lateinit var repo: Path

    protected val root by lazy { repo.toRealPath() }
    protected val runs = mutableListOf<List<String>>()
    protected val environments = mutableListOf<Map<String, String>>()
    protected val adapters = mutableListOf<GradleAdapter>()

    // What the fake Gradle answers: the query's JSON, and the output and exit code of the other runs.
    protected var query = """{"versions": {}, "classpath": [], "plugins": []}"""
    protected var queryOutput: ProcessResult? = null
    protected var compileOutput = ProcessResult(0, "")
    protected var testOutput = ProcessResult(0, "")

    @AfterTest
    fun `close every adapter`() = adapters.forEach { it.close() }

    protected fun task(command: List<String>) = command.firstOrNull { it.startsWith("lector") } ?: command.last()

    protected fun testFailure(
        task: String,
        test: String,
    ) = Diagnostic(null, null, null, null, "failed", Severity.ERROR, task, test)

    protected fun writeFailingReport(
        module: String,
        task: String,
        className: String,
    ): Path {
        val dir = if (module.isEmpty()) repo else repo.resolve(module)
        val report = dir.resolve("build/test-results/$task/TEST-$className.xml").createParentDirectories()
        report.writeText(
            """
            <testsuite name="$className" tests="1" failures="1">
              <testcase name="breaks" classname="$className"><failure message="expected" type="org.opentest4j.AssertionFailedError">boom</failure></testcase>
            </testsuite>
            """.trimIndent(),
        )
        return report
    }

    protected fun gradleProject(
        dir: Path = repo,
        gradle: String = "9.1.0",
        buildFile: String = "settings.gradle.kts",
    ) {
        dir.resolve(buildFile).writeText("")
        dir.resolve("gradlew").writeText("#!/bin/sh\n")
        wrapperProperties(dir).createParentDirectories().writeText(
            "distributionBase=GRADLE_USER_HOME\ndistributionUrl=https\\://services.gradle.org/distributions/gradle-$gradle-bin.zip\n",
        )
    }

    protected fun wrapperProperties(dir: Path = repo) = dir.resolve("gradle/wrapper/gradle-wrapper.properties")

    protected fun captured(name: String) = checkNotNull(javaClass.getResource("/gradle-output/$name")) { name }.readText()

    /** An adapter on a fake Gradle that answers like the fields above say. [before] runs first, e.g. to write reports. */
    protected fun adapter(
        init: Path? = null,
        jdks: Map<Int, Path> = emptyMap(),
        environment: Map<String, String> = emptyMap(),
        rewritePlugin: Path? = null,
        before: (List<String>) -> Unit = {},
    ) = GradleAdapter(init, jdks, environment, rewritePlugin) { command, workDir, variables, _ ->
        runs += command
        environments += variables
        assertEquals(command.first(), workDir.resolve("gradlew").toString())
        before(command)
        when {
            "--stop" in command -> {
                ProcessResult(0, "Stopping Daemon(s)\n1 Daemon stopped\n")
            }

            "lectorQuery" in command && queryOutput != null -> {
                queryOutput!!
            }

            "lectorQuery" in command -> {
                val output = command.single { it.startsWith("-Plector.queryOutput=") }.substringAfter('=')
                Path.of(output).writeText(query)
                ProcessResult(0, "")
            }

            "lectorCompile" in command -> {
                compileOutput
            }

            else -> {
                testOutput
            }
        }
    }.also { adapters += it }

    protected companion object {
        val JDKS = mapOf(17 to Path.of("/opt/java/17"), 21 to Path.of("/opt/java/21"), 25 to Path.of("/opt/java/25"))
    }
}
