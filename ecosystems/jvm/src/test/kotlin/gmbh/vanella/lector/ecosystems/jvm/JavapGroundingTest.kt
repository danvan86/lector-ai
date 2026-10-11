package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.UpdateType
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Grounding with the real `javap` on JARs built from small sources, laid out like a local Maven repository. */
class JavapGroundingTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var localRepository: Path

    private val change = DependencyChange("maven", "org.example:lib-core", "1.0", "2.0", UpdateType.MAJOR)
    private val grounding by lazy { JavapGrounding(localRepository) }
    private val app = "src/main/java/com/example/App.java"

    @BeforeTest
    fun `install both versions of lib-core and a sibling artifact`() {
        buildJar(
            artifact("lib-core", "1.0"),
            mapOf(
                "org.example.lib.Greeter" to
                    "package org.example.lib; public class Greeter { public String greetPolitely(String n) { return n; } }",
            ),
        )
        buildJar(
            artifact("lib-core", "2.0"),
            mapOf(
                "org.example.lib.Greeter" to
                    "package org.example.lib; public class Greeter { public String greet(String n) { return n; } " +
                    "private void helper() {} " +
                    "public static class Inner { public void inside() {} } }",
                "org.example.lib.package-info" to "@Deprecated package org.example.lib;",
                "org.example.lib.Farewell" to
                    "package org.example.lib; public class Farewell { public static String farewell(String n) { return n; } }",
                "org.example.lib.Unrelated" to "package org.example.lib; public class Unrelated { public void other() {} }",
                "org.example.lib.util.Names" to
                    "package org.example.lib.util; public class Names { public static String all() { return \"\"; } }",
            ),
        )
        buildJar(
            artifact("lib-extra", "2.0"),
            mapOf(
                "org.example.extra.Strings" to
                    "package org.example.extra; public class Strings { public static String trim(String s) { return s; } }",
            ),
        )
        // Not a JAR at all: must never be opened.
        localRepository.resolve("org/example/lib-core/2.0/lib-core-2.0-sources.jar").writeText("not a zip")
    }

    @Test
    fun `an imported class gets the public signatures of the new version`() {
        source(app, "package com.example;\n\nimport org.example.lib.Greeter;\nimport java.util.List;\n")

        val text =
            grounding.ground(
                repo,
                change,
                listOf(javac(app, "cannot find symbol\nsymbol:   method greetPolitely(java.lang.String)")),
            )

        assertContains(text, "javap -public of org.example:lib-core 2.0")
        assertContains(text, "public class org.example.lib.Greeter {")
        assertContains(text, "public java.lang.String greet(java.lang.String);")
        assertFalse("greetPolitely(" in text, "the old version's API is not shown")
        assertFalse("helper" in text, "private members are not shown")
        assertFalse("Unrelated" in text, "classes the errors don't refer to are not shown")
        assertFalse("Compiled from" in text)
        assertFalse("Inner" in text, "nested classes are left out")
    }

    @Test
    fun `a class in an imported package that declares the missing symbol is shown too`() {
        source(app, "package com.example;\n\nimport org.example.lib.Greeter;\n")

        val text = grounding.ground(repo, change, listOf(javac(app, "cannot find symbol\nsymbol:   method farewell(java.lang.String)")))

        assertContains(text, "public static java.lang.String farewell(java.lang.String);")
        assertContains(text, "public class org.example.lib.Greeter {")
        assertFalse("Unrelated" in text)
    }

    @Test
    fun `a class named in the error is found in any JAR of the new version, also through a Kotlin or static import`() {
        val kotlinFile = "src/main/kotlin/com/example/Texts.kt"
        source(kotlinFile, "package com.example\n\nimport org.example.extra.Strings as S\nimport org.example.lib.*\n")
        source(app, "package com.example;\n\nimport static org.example.lib.Farewell.farewell;\n")
        val diagnostics =
            listOf(
                Diagnostic(kotlinFile, 5, 3, null, "Unresolved reference 'Names'.", Severity.ERROR, "kotlinc"),
                javac(app, "incompatible types: org.example.lib.Greeter cannot be converted to java.lang.String"),
            )

        val text = grounding.ground(repo, change, diagnostics)

        assertContains(text, "public class org.example.lib.util.Names {")
        assertContains(text, "public class org.example.extra.Strings {")
        assertContains(text, "public class org.example.lib.Farewell {")
        assertContains(text, "public class org.example.lib.Greeter {")
        assertFalse("Unrelated" in text)
    }

    @Test
    fun `without JARs of the new version, or without classes the errors refer to, there is no grounding`() {
        source(app, "package com.example;\n\nimport java.util.List;\n")
        val error = javac(app, "cannot find symbol\nsymbol:   class Lisst")
        val buildError = Diagnostic(null, null, null, null, "Could not find artifact org.example:lib-core:jar:2.0", Severity.ERROR, "maven")

        assertEquals("", grounding.ground(repo, change, listOf(error, buildError)))
        assertEquals("", grounding.ground(repo, change.copy(toVersion = "3.0"), listOf(error)))
        assertEquals("", grounding.ground(repo, change.copy(`package` = "org.example:none"), listOf(error)))
        assertEquals("", grounding.ground(repo, change.copy(`package` = "no-coordinates"), listOf(error)))
        assertEquals("", grounding.ground(repo, change.copy(`package` = "com.other:none"), listOf(error)))
    }

    @Test
    fun `classes of a wildcard-imported package that don't declare the missing symbol are not shown`() {
        source(app, "package com.example;\n\nimport org.example.lib.*;\n")

        assertEquals("", grounding.ground(repo, change, listOf(javac(app, "cannot find symbol\nsymbol:   method nowhere()"))))
    }

    @Test
    fun `a failing javap run keeps the classes it could print`() {
        source(app, "package com.example;\n\nimport org.example.lib.Greeter;\n")
        val output =
            "Error: class not found: org.example.lib.Gone\n" +
                "Compiled from \"Greeter.java\"\npublic class org.example.lib.Greeter {\n}\n"
        val failing = JavapGrounding(localRepository) { _, _ -> ProcessResult(1, output) }

        val text = failing.ground(repo, change, listOf(javac(app, "cannot find symbol")))

        assertContains(text, "public class org.example.lib.Greeter {\n}")
        assertFalse("Error:" in text)
    }

    @Test
    fun `the Maven adapter grounds with the JARs of its local repository`() {
        source(app, "package com.example;\n\nimport org.example.lib.Greeter;\n")

        val text = MavenAdapter(localRepository = localRepository).ground(repo, change, listOf(javac(app, "cannot find symbol")))

        assertContains(text, "public class org.example.lib.Greeter {")
    }

    @Test
    fun `an error in a file that isn't in the repository still uses its message`() {
        val text = grounding.ground(repo, change, listOf(javac("src/Gone.java", "incompatible types: org.example.lib.Unrelated")))

        assertTrue("public class org.example.lib.Unrelated {" in text, text)
    }

    private fun artifact(
        name: String,
        version: String,
    ) = localRepository.resolve("org/example/$name/$version/$name-$version.jar")

    private fun source(
        file: String,
        text: String,
    ) = repo.resolve(file).createParentDirectories().writeText(text)

    private fun javac(
        file: String,
        message: String,
    ) = Diagnostic(file, 4, 9, null, message, Severity.ERROR, "javac")
}
