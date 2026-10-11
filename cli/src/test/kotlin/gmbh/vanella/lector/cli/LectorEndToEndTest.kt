package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.runProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Runs the installed `lector` start script as a separate process, the way the pipeline calls it. */
@Tag("e2e")
class LectorEndToEndTest {
    private val version = System.getProperty("lector.version")

    @Test
    fun `--version prints the version to stdout and exits with 0`() {
        val run = lector("--version")

        assertEquals("$version\n", run.stdout)
        assertEquals("", run.stderr)
        assertEquals(0, run.exitCode)
    }

    @Test
    fun `no arguments prints the help to stdout and exits with 0`() {
        val run = lector()

        assertContains(run.stdout, "Usage: lector")
        assertContains(run.stdout, "--version")
        assertEquals("", run.stderr)
        assertEquals(0, run.exitCode)
    }

    @Test
    fun `unknown option prints the error to stderr and exits with 2`() {
        val run = lector("--bogus")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "no such option --bogus")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `verify with invalid usage exits with 2`(
        @TempDir repo: Path,
    ) {
        val run = lector("verify", repo.toString(), "--format", "xml")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "invalid value for --format")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `logs go to stderr and keep stdout clean`() {
        val run = lector("--version", javaOpts = "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug")

        assertEquals("$version\n", run.stdout)
        assertContains(run.stderr, "Starting lector $version")
    }

    @Test
    fun `verify of a repository without a supported ecosystem exits with 2`(
        @TempDir repo: Path,
    ) {
        val run = lector("verify", repo.toString())

        assertEquals("", run.stdout)
        assertEquals("Error: No supported ecosystem in $repo. Supported: Maven (pom.xml at the root).\n", run.stderr)
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `verify with an invalid base ref exits with 2 before Maven runs`(
        @TempDir repo: Path,
    ) {
        repo.resolve("pom.xml").createFile()

        val run = lector("verify", repo.toString(), "--base", "no-such-ref", "--format", "json")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "Error: Base ref no-such-ref is not a commit in $repo")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `help lists exactly the verify and migrate commands`() {
        val run = lector("--help")

        val commands =
            run.stdout
                .substringAfter("Commands:")
                .lines()
                .filter { it.startsWith("  ") && !it.startsWith("   ") }
        assertEquals(listOf("verify", "migrate"), commands.map { it.trim().substringBefore(" ") })
    }

    @Test
    fun `migrate without --package exits with 2`(
        @TempDir repo: Path,
    ) {
        val run = lector("migrate", repo.toString(), "--from", "3.3.5", "--to", "3.4.1")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "missing option --package")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `migrate of a repository with uncommitted changes exits with 2`(
        @TempDir repo: Path,
    ) {
        git(repo, "init", "--quiet")
        repo.resolve("pom.xml").createFile()

        val run = lector("migrate", repo.toString(), "--package", PARENT, "--from", "3.3.5", "--to", "3.4.1")

        assertEquals("", run.stdout)
        assertContains(run.stderr, "has uncommitted changes. Commit or stash them first: pom.xml")
        assertEquals(2, run.exitCode)
    }

    @Test
    fun `migrate finds the recipe in the bundled index and runs it with the project's Maven`(
        @TempDir repo: Path,
    ) {
        mavenProject(repo)

        val run = lector("migrate", repo.toString(), "--package", PARENT, "--from", "3.3.5", "--to", "3.4.1")

        assertContains(run.stdout, "Recipe (applied)\n")
        assertContains(run.stdout, "  Recipe: org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4, leaf 3.4.x at depth 1\n")
        assertContains(run.stdout, "  Artifact: org.openrewrite.recipe:rewrite-spring:")
        assertContains(run.stdout, "Caused by the update (0)")
        assertEquals(0, run.exitCode, run.stderr)
        assertContains(repo.resolve("mvn.log").readText(), "org.openrewrite.maven:rewrite-maven-plugin:")
    }

    @Test
    fun `migrate --format json prints one JSON object with the recipe section and the verification`(
        @TempDir repo: Path,
    ) {
        mavenProject(repo)

        val run =
            lector("migrate", repo.toString(), "--package", "com.example:no-recipe", "--from", "1.0", "--to", "2.0", "--format", "json")

        val json = Json.parseToJsonElement(run.stdout).jsonObject
        assertEquals(listOf("recipe", "verification"), json.keys.toList())
        assertEquals(
            "noMatch",
            json
                .getValue("recipe")
                .jsonObject
                .getValue("outcome")
                .jsonPrimitive.content,
        )
        assertEquals(0, run.exitCode, run.stderr)
    }

    /**
     * A committed Maven project whose wrapper stands in for Maven: it logs its arguments, writes an effective POM with
     * the Spring Boot parent where `help:effective-pom` asks for one, and succeeds.
     */
    private fun mavenProject(repo: Path) {
        repo.resolve("pom.xml").writeText("<project/>\n")
        val wrapper = repo.resolve("mvnw")
        wrapper.writeText(
            """
            #!/bin/sh
            echo "${'$'}*" >> "${'$'}(dirname "${'$'}0")/mvn.log"
            for arg in "${'$'}@"; do
              case "${'$'}arg" in
                -Doutput=*) echo '<project><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.4.1</version></parent></project>' > "${'$'}{arg#-Doutput=}" ;;
              esac
            done
            exit 0
            """.trimIndent() + "\n",
        )
        wrapper.toFile().setExecutable(true)
        repo.resolve(".gitignore").writeText("mvn.log\n")
        git(repo, "init", "--quiet")
        git(repo, "add", ".")
        git(
            repo,
            "-c",
            "user.name=Lector",
            "-c",
            "user.email=lector@example.com",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "--quiet",
            "-m",
            "head",
        )
    }

    private fun git(
        repo: Path,
        vararg args: String,
    ) {
        val result = runProcess(listOf("git") + args, repo, 30.seconds)
        assertEquals(0, result.exitCode, result.output)
    }

    private data class Run(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    // Both streams go to files, so a full pipe buffer can't block the process. The timeout keeps a hanging run from blocking the build.
    private fun lector(
        vararg args: String,
        javaOpts: String = "",
    ): Run {
        val stdout = File.createTempFile("lector", ".out").apply { deleteOnExit() }
        val stderr = File.createTempFile("lector", ".err").apply { deleteOnExit() }
        val process =
            ProcessBuilder(System.getProperty("lector.bin"), *args)
                .redirectOutput(stdout)
                .redirectError(stderr)
                .apply {
                    environment()["JAVA_HOME"] = System.getProperty("java.home")
                    environment()["JAVA_OPTS"] = javaOpts
                }.start()
        val exited = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!exited) process.destroyForcibly()
        assertTrue(exited, "lector did not exit within $TIMEOUT_SECONDS seconds")
        return Run(process.exitValue(), stdout.readText(), stderr.readText())
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
        const val PARENT = "org.springframework.boot:spring-boot-starter-parent"
    }
}
