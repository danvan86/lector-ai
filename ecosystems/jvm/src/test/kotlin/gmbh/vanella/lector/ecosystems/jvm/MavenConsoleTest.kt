package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** Parses Maven console output captured from the fixture repos (see `fixtures/README.md`). */
class MavenConsoleTest {
    private val repo = Path.of("/work/repo")

    @Test
    fun `javac errors in a multi-module build, with paths relative to the repository root`() {
        assertEquals(
            listOf(
                javac(
                    "api/src/test/java/com/example/api/GreeterTest.java",
                    10,
                    49,
                    "cannot find symbol\nsymbol:   method greetPolitely(java.lang.String)\nlocation: class com.example.api.Greeter",
                ),
                javac(
                    "app/src/main/java/com/example/app/App.java",
                    8,
                    9,
                    "cannot find symbol\nsymbol:   class Strng\nlocation: class com.example.app.App",
                ),
            ),
            parse("javac-errors.log"),
        )
    }

    @Test
    fun `kotlinc errors`() {
        assertEquals(
            listOf(
                kotlinc(4, 56, "Unresolved reference 'capitalized' on receiver of type 'String'."),
                kotlinc(6, 43, "Return type mismatch: expected 'Int', actual 'String'."),
            ),
            parse("kotlinc-errors.log"),
        )
    }

    @Test
    fun `kotlinc errors in the format of Kotlin 1_x`() {
        assertEquals(
            listOf(
                kotlinc(4, 56, "Unresolved reference: capitalized"),
                kotlinc(6, 43, "Type mismatch: inferred type is String but Int was expected"),
            ),
            parse("kotlinc-1.9-errors.log"),
        )
    }

    @Test
    fun `a compiler crash is a build error, because no compile error explains it`() {
        assertEquals(
            listOf(
                buildError(
                    """
                    Failed to execute goal org.jetbrains.kotlin:kotlin-maven-plugin:1.9.25:compile (compile) on project kotlin-compile-errors: Compilation failure
                    java.lang.IllegalArgumentException: 25.0.1
                    	at com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:305)
                    	at com.intellij.util.lang.JavaVersion.current(JavaVersion.java:174)
                    	at org.jetbrains.kotlin.cli.jvm.modules.JavaVersionUtilsKt.isAtLeastJava9(javaVersionUtils.kt:11)
                    """.trimIndent(),
                ),
            ),
            parse("kotlinc-crash.log"),
        )
    }

    @Test
    fun `a dependency that can't be resolved is a build error without a location`() {
        assertEquals(
            listOf(
                buildError(
                    """
                    Failed to execute goal on project missing-dependency: Could not resolve dependencies for project com.example:missing-dependency:jar:1.0
                    dependency: org.apache.commons:commons-lang3:jar:99.0.0 (compile)
                    	Could not find artifact org.apache.commons:commons-lang3:jar:99.0.0 in central (https://repo.maven.apache.org/maven2)
                    """.trimIndent(),
                ),
            ),
            parse("dependency-resolution.log"),
        )
    }

    @Test
    fun `a parent POM that can't be resolved is a build error without a location`() {
        val problem =
            "Non-resolvable parent POM for com.example:missing-parent:1.0: The following artifacts could not be resolved: " +
                "org.springframework.boot:spring-boot-starter-parent:pom:99.0.0 (absent): Could not find artifact " +
                "org.springframework.boot:spring-boot-starter-parent:pom:99.0.0 in central (https://repo.maven.apache.org/maven2) " +
                "and 'parent.relativePath' points at wrong local POM @ line 4, column 11"

        assertEquals(listOf(buildError(problem)), parse("missing-parent.log"))
    }

    @Test
    fun `test failures in the console are left to the test reports`() {
        assertEquals(emptyList(), parse("test-failures.log"))
    }

    @Test
    fun `a goal failure that ends the output is still reported`() {
        val output = "[ERROR] Failed to execute goal org.example:plugin:1.0:check (default) on project app: Rule violated"

        assertEquals(
            listOf(buildError("Failed to execute goal org.example:plugin:1.0:check (default) on project app: Rule violated")),
            MavenConsole.parse(output, repo),
        )
    }

    @Test
    fun `failures that follow each other directly are reported one by one`() {
        val output =
            """
            [ERROR] Failed to execute goal org.example:plugin:1.0:check (default) on project api: Rule violated
            [ERROR] Failed to execute goal org.example:plugin:1.0:check (default) on project app: Rule violated
            """.trimIndent()

        assertEquals(
            listOf(
                buildError("Failed to execute goal org.example:plugin:1.0:check (default) on project api: Rule violated"),
                buildError("Failed to execute goal org.example:plugin:1.0:check (default) on project app: Rule violated"),
            ),
            MavenConsole.parse(output, repo),
        )
    }

    @Test
    fun `a line that is not an error ends the explanation of a failure`() {
        val output = "[ERROR] Failed to execute goal on project app: Broken\n[INFO] Building lib 1.0\n[ERROR] Unrelated"

        assertEquals(listOf(buildError("Failed to execute goal on project app: Broken")), MavenConsole.parse(output, repo))
    }

    @Test
    fun `an empty line ends the message of a compile error`() {
        val output = "[ERROR] /work/repo/App.java:[1,2] cannot find symbol\n\n  not part of the message"

        assertEquals(listOf(javac("App.java", 1, 2, "cannot find symbol")), MavenConsole.parse(output, repo))
    }

    @Test
    fun `a compile error outside the repository keeps its absolute path`() {
        val output = "[ERROR] /elsewhere/Generated.java:[1,2] cannot find symbol"

        assertEquals(listOf(javac("/elsewhere/Generated.java", 1, 2, "cannot find symbol")), MavenConsole.parse(output, repo))
    }

    @Test
    fun `warnings and info lines are not problems`() {
        val output =
            """
            [WARNING] /work/repo/src/main/java/App.java:[3,8] [deprecation] old() in Api has been deprecated
            [WARNING] file:///work/repo/src/main/kotlin/App.kt:3:8 'old' is deprecated.
            [INFO] /work/repo/src/main/java/App.java:[3,8] note
            """.trimIndent()

        assertEquals(emptyList(), MavenConsole.parse(output, repo))
    }

    private fun parse(log: String): List<Diagnostic> {
        val output = requireNotNull(javaClass.getResource("/maven-output/$log")) { "$log is missing" }.readText()
        return MavenConsole.parse(output, repo)
    }

    private fun javac(
        file: String,
        line: Int,
        column: Int,
        message: String,
    ) = Diagnostic(file, line, column, null, message, Severity.ERROR, "javac")

    private fun kotlinc(
        line: Int,
        column: Int,
        message: String,
    ) = Diagnostic("src/main/kotlin/com/example/Greeter.kt", line, column, null, message, Severity.ERROR, "kotlinc")

    private fun buildError(message: String) = Diagnostic(null, null, null, null, message, Severity.ERROR, "maven")
}
