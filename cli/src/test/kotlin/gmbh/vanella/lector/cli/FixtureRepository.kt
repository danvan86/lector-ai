package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.runProcess
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.exists
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * A Git repository in [dir], built commit by commit from the fixture `/fixtures/<fixture>/<commit>`: each commit copies
 * its files over the ones before. A Gradle fixture brings only its `gradle-wrapper.properties`; the first commit adds
 * the tool's own wrapper script and JAR (`lector.gradleWrapper`).
 */
internal class FixtureRepository(
    private val dir: Path,
    private val fixture: String,
) {
    @OptIn(ExperimentalPathApi::class)
    fun commit(
        files: String,
        message: String,
    ) {
        val source = requireNotNull(javaClass.getResource("/fixtures/$fixture/$files")).toURI().toPath()
        source.copyToRecursively(dir, followLinks = false, overwrite = true)
        if (git("rev-parse", "--is-inside-work-tree", check = false) != "true\n") {
            git("init", "--quiet")
            // Gradle leaves dotfiles out of test resources, so the fixture can't carry its own .gitignore.
            dir.resolve(".gitignore").writeText("target/\nbuild/\n.gradle/\n")
            if (dir.resolve("gradle/wrapper/gradle-wrapper.properties").exists()) addGradleWrapper()
        }
        git("add", ".")
        git("commit", "--quiet", "-m", message)
    }

    private fun addGradleWrapper() {
        val wrapper = Path.of(checkNotNull(System.getProperty("lector.gradleWrapper")) { "run with :cli:integrationTest" })
        wrapper.resolve("gradlew").copyTo(dir.resolve("gradlew"))
        wrapper.resolve("gradle/wrapper/gradle-wrapper.jar").copyTo(dir.resolve("gradle/wrapper/gradle-wrapper.jar"))
        dir.resolve("gradlew").toFile().setExecutable(true)
    }

    fun git(
        vararg args: String,
        check: Boolean = true,
    ): String {
        val identity = listOf("-c", "user.name=Lector", "-c", "user.email=lector@example.com", "-c", "commit.gpgsign=false")
        val result = runProcess(listOf("git") + identity + args, dir, 1.minutes)
        if (check) assertEquals(0, result.exitCode, result.output)
        return result.output
    }
}
