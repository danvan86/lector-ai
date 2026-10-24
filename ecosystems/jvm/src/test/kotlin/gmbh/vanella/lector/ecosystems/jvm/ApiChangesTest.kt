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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the model learns about an update's API changes (M10): a Maven project whose old and new versions of
 * `org.example:lib-core` are in the local repository, errors from javac and kotlinc as they really read, and the
 * adapter's `apiChanges` as the model gets it. Every scenario builds its own two versions of the library.
 */
class ApiChangesTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var localRepository: Path

    private val change = DependencyChange("maven", "org.example:lib-core", "1.0", "2.0", UpdateType.MAJOR)
    private val app = "src/main/java/com/example/App.java"

    // Grounding reads only the local repository here: a Maven call would mean the old JARs had to be fetched.
    private val mavenCalls = mutableListOf<List<String>>()
    private val adapter by lazy {
        MavenAdapter(localRepository = localRepository) { command, _, _, _ ->
            mavenCalls += command
            ProcessResult(1, "unexpected Maven call")
        }
    }

    @Test
    fun `a class that moved to another package is named with its new place`() {
        library(
            old = mapOf("org.example.util.Names" to cls("org.example.util", "Names", "public static String all() { return \"\"; }")),
            new =
                mapOf(
                    "org.example.text.Names" to cls("org.example.text", "Names", "public static String all() { return \"\"; }"),
                    "org.example.util.Other" to cls("org.example.util", "Other", ""),
                ),
            oldExtra = mapOf("org.example.util.Other" to cls("org.example.util", "Other", "")),
        )
        source(app, "package com.example;\n\nimport org.example.util.Names;\n")

        val text =
            adapter.apiChanges(
                repo,
                change,
                listOf(javac(app, "cannot find symbol\nsymbol:   class Names\nlocation: package org.example.util")),
            )

        assertTrue(text.startsWith("API changes of org.example:lib-core from 1.0 to 2.0"), text)
        assertContains(text, "Moved classes:\n- org.example.util.Names → org.example.text.Names")
        assertTrue(mavenCalls.isEmpty(), "old JARs in the local repository need no Maven call")
    }

    @Test
    fun `a package whose classes all moved together is one line, a single moved class is not a renamed package`() {
        library(
            old =
                mapOf(
                    "org.example.old.Greeter" to cls("org.example.old", "Greeter", "public String greet(String n) { return n; }"),
                    "org.example.old.Farewell" to cls("org.example.old", "Farewell", "public String bye(String n) { return n; }"),
                    "org.example.old.Wave" to cls("org.example.old", "Wave", ""),
                ),
            new =
                mapOf(
                    "org.example.lib.Greeter" to cls("org.example.lib", "Greeter", "public String greet(String n) { return n; }"),
                    "org.example.lib.Farewell" to cls("org.example.lib", "Farewell", "public String bye(String n) { return n; }"),
                    "org.example.lib.Wave" to cls("org.example.lib", "Wave", ""),
                ),
        )
        source(
            app,
            "package com.example;\n\nimport org.example.old.Greeter;\nimport org.example.old.Farewell;\nimport org.example.old.Wave;\n",
        )

        val text = adapter.apiChanges(repo, change, listOf(javac(app, "package org.example.old does not exist")))

        assertContains(text, "Renamed packages:\n- org.example.old → org.example.lib")
        assertFalse("Moved classes:" in text, "the classes of a renamed package aren't listed one by one:\n$text")
    }

    @Test
    fun `a class that is gone without a successor is a removed class`() {
        library(
            old =
                mapOf(
                    "org.example.lib.Legacy" to cls("org.example.lib", "Legacy", ""),
                    "org.example.lib.Kept" to cls("org.example.lib", "Kept", ""),
                ),
            new = mapOf("org.example.lib.Kept" to cls("org.example.lib", "Kept", "")),
        )
        source(app, "package com.example;\n\nimport org.example.lib.Legacy;\n")

        val text = adapter.apiChanges(repo, change, listOf(javac(app, "cannot find symbol\nsymbol:   class Legacy")))

        assertContains(text, "Removed classes:\n- org.example.lib.Legacy")
    }

    @Test
    fun `a method whose parameters changed shows its old and new overloads, an added overload alone is no change`() {
        library(
            old =
                mapOf(
                    "org.example.lib.Calc" to
                        cls("org.example.lib", "Calc", "public int add(int a, int b) { return a; } public int sub(int a) { return a; }"),
                ),
            new =
                mapOf(
                    "org.example.lib.Calc" to
                        cls(
                            "org.example.lib",
                            "Calc",
                            "public int add(int a, int b, int c) { return a; } public int sub(int a) { return a; } public int sub(int a, int b) { return a; }",
                        ),
                ),
        )
        source(app, "package com.example;\n\nimport org.example.lib.Calc;\n")

        val text =
            adapter.apiChanges(
                repo,
                change,
                listOf(javac(app, "method add in class org.example.lib.Calc cannot be applied to given types;")),
            )

        assertContains(text, "Changed signatures:\n- org.example.lib.Calc.add: (int, int) → (int, int, int)")
        assertFalse("sub" in text, "a method that only gained an overload didn't change:\n$text")
    }

    @Test
    fun `a static method that moved to a class the code doesn't import is a moved member, even with another return type`() {
        library(
            old =
                mapOf(
                    "org.example.lib.Strings" to
                        cls(
                            "org.example.lib",
                            "Strings",
                            "public static String describe(Object o) { return \"\"; } public static String trim(String s) { return s; }",
                        ),
                ),
            new =
                mapOf(
                    "org.example.lib.Strings" to cls("org.example.lib", "Strings", "public static String trim(String s) { return s; }"),
                    "org.example.fmt.Describer" to
                        cls("org.example.fmt", "Describer", "public static Object describe(Object o) { return o; }"),
                ),
        )
        source(app, "package com.example;\n\nimport org.example.lib.Strings;\n")

        val text =
            adapter.apiChanges(
                repo,
                change,
                listOf(
                    javac(app, "cannot find symbol\nsymbol:   method describe(java.lang.String)\nlocation: class org.example.lib.Strings"),
                ),
            )

        assertContains(
            text,
            "Moved members:\n- org.example.lib.Strings.describe(java.lang.Object) → org.example.fmt.Describer.describe(java.lang.Object)",
        )
    }

    @Test
    fun `an instance method that exists in another class is removed, not moved`() {
        library(
            old = mapOf("org.example.lib.Pipe" to cls("org.example.lib", "Pipe", "public void close() {} public void open() {}")),
            new =
                mapOf(
                    "org.example.lib.Pipe" to cls("org.example.lib", "Pipe", "public void open() {}"),
                    "org.example.io.Stream" to cls("org.example.io", "Stream", "public void close() {}"),
                ),
        )
        source(app, "package com.example;\n\nimport org.example.lib.Pipe;\n")

        val text =
            adapter.apiChanges(
                repo,
                change,
                listOf(javac(app, "cannot find symbol\nsymbol:   method close()\nlocation: variable pipe of type org.example.lib.Pipe")),
            )

        assertContains(text, "Removed members:\n- org.example.lib.Pipe.close()")
        assertFalse("Moved members:" in text, "an instance method in another class isn't the same method:\n$text")
    }

    @Test
    fun `a method replaced by exactly one new method of the same signature is a rename candidate, two of them are not`() {
        library(
            old =
                mapOf(
                    "org.example.lib.Greeter" to cls("org.example.lib", "Greeter", "public String greetPolitely(String n) { return n; }"),
                    "org.example.lib.Twins" to cls("org.example.lib", "Twins", "public void a(int x) {} public void b(int x) {}"),
                ),
            new =
                mapOf(
                    "org.example.lib.Greeter" to cls("org.example.lib", "Greeter", "public String greet(String n) { return n; }"),
                    "org.example.lib.Twins" to cls("org.example.lib", "Twins", "public void c(int x) {} public void d(int x) {}"),
                ),
        )
        source(app, "package com.example;\n\nimport org.example.lib.Greeter;\nimport org.example.lib.Twins;\n")
        val errors =
            listOf(
                javac(
                    app,
                    "cannot find symbol\nsymbol:   method greetPolitely(java.lang.String)\nlocation: variable g of type org.example.lib.Greeter",
                ),
                javac(app, "cannot find symbol\nsymbol:   method a(int)\nlocation: variable t of type org.example.lib.Twins"),
            )

        val text = adapter.apiChanges(repo, change, errors)

        assertContains(
            text,
            "Rename candidates:\n- org.example.lib.Greeter.greetPolitely(java.lang.String) → org.example.lib.Greeter.greet(java.lang.String)",
        )
        assertContains(text, "- org.example.lib.Twins.a(int)")
        assertFalse("Twins.a(int) →" in text, "two possible renames are no candidate:\n$text")
    }

    @Test
    fun `a Kotlin import of a package kotlinc can't resolve is diffed, compiler-generated members are left out`() {
        library(
            old =
                mapOf(
                    "mu.KotlinLogging" to
                        cls("mu", "KotlinLogging", "public static Object logger() { return null; } public static void access\$000() {}"),
                ),
            new =
                mapOf(
                    "io.github.oshai.kotlinlogging.KotlinLogging" to
                        cls(
                            "io.github.oshai.kotlinlogging",
                            "KotlinLogging",
                            "public static Object logger() { return null; } public static void access\$000() {}",
                        ),
                ),
        )
        val file = "src/main/kotlin/com/example/Service.kt"
        source(file, "package com.example\n\nimport mu.KotlinLogging\n")

        val text =
            adapter.apiChanges(
                repo,
                change,
                listOf(Diagnostic(file, 3, 8, null, "Unresolved reference 'mu'.", Severity.ERROR, "kotlinc")),
            )

        assertContains(text, "- mu.KotlinLogging → io.github.oshai.kotlinlogging.KotlinLogging")
        assertFalse("access\$000" in text, "compiler-generated members are noise:\n$text")
    }

    @Test
    fun `a changed class the errors don't refer to is left out`() {
        library(
            old =
                mapOf(
                    "org.example.lib.Greeter" to cls("org.example.lib", "Greeter", "public String greetPolitely(String n) { return n; }"),
                    "org.example.lib.Unrelated" to cls("org.example.lib", "Unrelated", "public void other() {}"),
                ),
            new =
                mapOf(
                    "org.example.lib.Greeter" to cls("org.example.lib", "Greeter", "public String greet(String n) { return n; }"),
                    "org.example.lib.Unrelated" to cls("org.example.lib", "Unrelated", ""),
                ),
        )
        source(app, "package com.example;\n\nimport org.example.lib.Greeter;\n")

        val text =
            adapter.apiChanges(
                repo,
                change,
                listOf(
                    javac(
                        app,
                        "cannot find symbol\nsymbol:   method greetPolitely(java.lang.String)\nlocation: variable g of type org.example.lib.Greeter",
                    ),
                ),
            )

        assertContains(text, "org.example.lib.Greeter.greetPolitely")
        assertFalse("Unrelated" in text, "only classes the errors refer to are diffed:\n$text")
    }

    @Test
    fun `nothing changed in the classes the errors refer to adds nothing`() {
        val same = mapOf("org.example.lib.Greeter" to cls("org.example.lib", "Greeter", "public String greet(String n) { return n; }"))
        library(old = same, new = same)
        source(app, "package com.example;\n\nimport org.example.lib.Greeter;\n")

        assertEquals(
            "",
            adapter.apiChanges(repo, change, listOf(javac(app, "incompatible types: org.example.lib.Greeter cannot be converted to int"))),
        )
    }

    @Test
    fun `each limit lists the first entries in order and counts the rest`() {
        // Moved classes: 16 classes, each from its own package, so none of them is a renamed package.
        val moved = (1..16).map { it.toString().padStart(2, '0') }
        library(
            old =
                moved.associate { "org.example.p$it.C$it" to cls("org.example.p$it", "C$it", "") } +
                    mapOf("org.example.lib.Many" to cls("org.example.lib", "Many", (1..16).joinToString(" ") { "public void m$it() {}" })) +
                    mapOf(
                        "org.example.lib.Sig" to cls("org.example.lib", "Sig", (1..11).joinToString(" ") { "public void s$it(int a) {}" }),
                    ) +
                    (1..11).associate { "org.example.r.R$it" to cls("org.example.r", "R$it", "public void before$it(int a) {}") },
            new =
                moved.associate { "org.example.q$it.C$it" to cls("org.example.q$it", "C$it", "") } +
                    mapOf("org.example.lib.Many" to cls("org.example.lib", "Many", "")) +
                    mapOf(
                        "org.example.lib.Sig" to cls("org.example.lib", "Sig", (1..11).joinToString(" ") { "public void s$it(long a) {}" }),
                    ) +
                    (1..11).associate { "org.example.r.R$it" to cls("org.example.r", "R$it", "public void after$it(int a) {}") },
        )
        val imports =
            moved.joinToString("") { "import org.example.p$it.C$it;\n" } +
                "import org.example.lib.Many;\nimport org.example.lib.Sig;\n" +
                (1..11).joinToString("") { "import org.example.r.R$it;\n" }
        source(app, "package com.example;\n\n$imports")
        val errors =
            moved.map { javac(app, "package org.example.p$it does not exist") } +
                (1..16).map {
                    javac(
                        app,
                        "cannot find symbol\nsymbol:   method m$it()\nlocation: variable x of type org.example.lib.Many",
                    )
                } +
                (1..11).map {
                    javac(
                        app,
                        "cannot find symbol\nsymbol:   method s$it(int)\nlocation: variable x of type org.example.lib.Sig",
                    )
                } +
                (1..11).map {
                    javac(
                        app,
                        "cannot find symbol\nsymbol:   method before$it(int)\nlocation: variable x of type org.example.r.R$it",
                    )
                }

        val text = adapter.apiChanges(repo, change, errors)

        assertEquals(15, block(text, "Moved classes:").count { it.startsWith("- org.example.p") }, text)
        assertEquals("- … and 1 more", block(text, "Moved classes:").last())
        assertEquals("- org.example.p01.C01 → org.example.q01.C01", block(text, "Moved classes:").first(), "sorted by name")
        assertEquals(15, block(text, "Removed members:").count { it.startsWith("- org.example.lib.Many.") }, text)
        assertEquals("- … and 1 more", block(text, "Removed members:").last())
        assertEquals(10, block(text, "Changed signatures:").count { it.startsWith("- org.example.lib.Sig.") }, text)
        assertEquals("- … and 1 more", block(text, "Changed signatures:").last())
        assertEquals(10, block(text, "Rename candidates:").count { it.startsWith("- org.example.r.") }, text)
        assertEquals("- … and 1 more", block(text, "Rename candidates:").last())
    }

    /** The lines of the block under [heading], up to the next blank line or heading. */
    private fun block(
        text: String,
        heading: String,
    ): List<String> {
        val lines = text.lines()
        val start = lines.indexOf(heading)
        assertTrue(start >= 0, "no block \"$heading\" in:\n$text")
        return lines.drop(start + 1).takeWhile { it.startsWith("- ") }
    }

    private fun library(
        old: Map<String, String>,
        new: Map<String, String>,
        oldExtra: Map<String, String> = emptyMap(),
    ) {
        buildJar(localRepository.resolve("org/example/lib-core/1.0/lib-core-1.0.jar"), old + oldExtra)
        buildJar(localRepository.resolve("org/example/lib-core/2.0/lib-core-2.0.jar"), new)
    }

    private fun cls(
        pkg: String,
        name: String,
        body: String,
    ) = "package $pkg; public class $name { $body }"

    private fun source(
        file: String,
        text: String,
    ) = repo.resolve(file).createParentDirectories().writeText(text)

    private fun javac(
        file: String,
        message: String,
    ) = Diagnostic(file, 3, 8, null, message, Severity.ERROR, "javac")
}
