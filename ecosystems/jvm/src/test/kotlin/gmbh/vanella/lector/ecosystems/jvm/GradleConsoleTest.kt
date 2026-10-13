package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Parses Gradle output captured from the fixture repos (`src/test/resources/gradle-output/`, see the fixtures README). */
class GradleConsoleTest {
    private val repo = Path.of("/work/repo")

    @Test
    fun `javac errors get file, line, the caret's column, and the details as part of the message`() {
        val problems = GradleConsole.parse(captured("javac-errors.log"), repo)

        assertEquals(
            listOf(
                javac(
                    "src/main/java/com/example/Greeter.java",
                    7,
                    39,
                    "cannot find symbol\nsymbol:   method capitalizeFully(String)\nlocation: class StringUtils",
                ),
                javac("src/main/java/com/example/Counter.java", 5, 36, "incompatible types: int cannot be converted to String"),
                javac("src/main/java/com/example/Counter.java", 6, 16, "incompatible types: String cannot be converted to int"),
            ),
            problems,
        )
    }

    @Test
    fun `kotlinc errors get file, line, column, and message`() {
        val problems = GradleConsole.parse(captured("kotlinc-errors.log"), repo)

        assertEquals(
            listOf(
                kotlinc("src/main/kotlin/com/example/Greeter.kt", 6, 55, "Unresolved reference 'capitalizeFully'."),
                kotlinc("src/main/kotlin/com/example/Greeter.kt", 8, 36, "Unresolved reference 'capitalized'."),
            ),
            problems,
        )
    }

    @Test
    fun `an error in a Kotlin build script is located in the script`() {
        val problems = GradleConsole.parse(captured("build-script-error.log"), repo)

        assertEquals(listOf(kotlinc("build.gradle.kts", 7, 5, "Unresolved reference 'undefinedSetting'.")), problems)
    }

    @Test
    fun `a failure without a compiler error becomes a diagnostic without a location, with where it happened`() {
        val problems = GradleConsole.parse(captured("groovy-script-error.log"), repo)

        assertEquals(
            listOf(
                gradle(
                    "Build file '/work/repo/build.gradle' line: 10\n" +
                        "A problem occurred evaluating root project 'gradle-groovy'.\n" +
                        "> Could not set unknown property 'undefinedSetting' for task ':test' of type org.gradle.api.tasks.testing.Test.",
                ),
            ),
            problems,
        )
    }

    @Test
    fun `a dependency that can't be found is a build failure whose cluster key is the artifact`() {
        val problem = GradleConsole.parse(captured("dependency-resolution.log"), repo).single()

        assertEquals(
            "Execution failed for task ':compileJava'.\n" +
                "> Could not resolve all files for configuration ':compileClasspath'.\n" +
                "   > Could not find org.apache.commons:commons-lang3:99.0.0.\n" +
                "     Searched in the following locations:\n" +
                "       - https://repo.maven.apache.org/maven2/org/apache/commons/commons-lang3/99.0.0/commons-lang3-99.0.0.pom\n" +
                "     Required by:\n" +
                "         root project :",
            problem.message,
        )
        assertEquals("artifact:org.apache.commons:commons-lang3", ClusterKeys.of(problem))
    }

    @Test
    fun `test failures come from the reports, so their summaries are no diagnostics`() {
        assertEquals(emptyList(), GradleConsole.parse(captured("test-failures.log"), repo))
    }

    @Test
    fun `compile errors in a subproject are relative to the repository root, custom source sets included`() {
        val problems = GradleConsole.parse(captured("integration-test-compile-error.log"), repo)

        assertEquals(
            listOf(
                "app/src/integrationTest/java/com/example/app/GreetingIT.java" to 10,
                "app/src/integrationTest/java/com/example/app/GreetingIT.java" to 15,
            ),
            problems.map { it.file to it.line },
        )
    }

    @Test
    fun `a compile summary without parsed compiler errors is kept, so the build never looks green`() {
        val withoutErrors = captured("javac-errors.log").lines().filterNot { it.startsWith("/work/repo/") }.joinToString("\n")

        val problem = GradleConsole.parse(withoutErrors, repo).single()

        assertEquals("gradle", problem.tool)
        assertEquals(
            "Execution failed for task ':compileJava'.\n> Compilation failed; see the compiler output below.",
            problem.message
                .lines()
                .take(2)
                .joinToString("\n"),
        )
    }

    @Test
    fun `a javac error whose source line starts at column 0 keeps its caret and details`() {
        assertEquals(
            listOf(javac("src/main/java/com/example/Counter.java", 3, 30, "cannot find symbol\nsymbol: class MissingBase")),
            GradleConsole.parse(captured("javac-column-zero.log"), repo),
        )
    }

    @Test
    fun `a javac error on the last line of the output, and a failure at its start, are read too`() {
        val error = captured("javac-errors.log").lines().first { it.startsWith("/work/repo/") }
        val failure = captured("groovy-script-error.log").lines().dropWhile { it != "* What went wrong:" }.joinToString("\n")

        assertEquals(listOf("cannot find symbol"), GradleConsole.parse(error, repo).map { it.message })
        assertEquals(listOf(null), GradleConsole.parse("$error\n3 errors\n", repo).map { it.column }, "no source line and caret")
        assertEquals(listOf(null), GradleConsole.parse(failure, repo).map { it.file })
    }

    @Test
    fun `reads the Java version of a missing toolchain, in the wording of Gradle 8_8 and newer and of older versions`() {
        assertEquals(11, GradleConsole.missingToolchain(captured("toolchain-missing.log")))
        assertEquals(11, GradleConsole.missingToolchain(captured("toolchain-missing-8.5.log")))
        assertNull(GradleConsole.missingToolchain(captured("javac-errors.log")))
    }

    private fun javac(
        file: String,
        line: Int,
        column: Int,
        message: String,
    ) = Diagnostic(file, line, column, null, message, Severity.ERROR, "javac")

    private fun kotlinc(
        file: String,
        line: Int,
        column: Int,
        message: String,
    ) = Diagnostic(file, line, column, null, message, Severity.ERROR, "kotlinc")

    private fun gradle(message: String) = Diagnostic(null, null, null, null, message, Severity.ERROR, "gradle")

    private fun captured(name: String) = checkNotNull(javaClass.getResource("/gradle-output/$name")) { name }.readText()
}
