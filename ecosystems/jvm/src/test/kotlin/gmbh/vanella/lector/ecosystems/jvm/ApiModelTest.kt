package gmbh.vanella.lector.ecosystems.jvm

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

/** The public API read from class files with the ClassFile API, on JARs built in the test and a real Kotlin release. */
class ApiModelTest {
    @TempDir
    lateinit var dir: Path

    private fun model(sources: Map<String, String>): ApiModel {
        val jar = dir.resolve("lib.jar")
        buildJar(jar, sources)
        return ApiModel.read(listOf(jar))
    }

    @Test
    fun `methods, constructors, and fields with erased parameter types and static-ness`() {
        val api =
            model(
                mapOf(
                    "org.example.lib.Box" to
                        """
                        package org.example.lib;
                        import java.util.List;
                        public class Box<T extends Number> {
                            public static final int LIMIT = 3;
                            public String label;
                            public Box(T value, List<String> tags) {}
                            public <R extends CharSequence> R map(T value, List<? extends R> into, int[][] grid) { return null; }
                            public static String join(String... parts) { return ""; }
                            protected void hidden() {}
                            void packagePrivate() {}
                        }
                        """.trimIndent(),
                ),
            )

        assertEquals(
            setOf(
                ApiMember(MemberKind.FIELD, "LIMIT", emptyList(), static = true, returnType = "int"),
                ApiMember(MemberKind.FIELD, "label", emptyList(), static = false, returnType = "java.lang.String"),
                ApiMember(
                    MemberKind.CONSTRUCTOR,
                    "<init>",
                    listOf("java.lang.Number", "java.util.List"),
                    static = false,
                    returnType = "void",
                ),
                ApiMember(
                    MemberKind.METHOD,
                    "map",
                    listOf("java.lang.Number", "java.util.List", "int[][]"),
                    static = false,
                    returnType = "java.lang.CharSequence",
                ),
                ApiMember(MemberKind.METHOD, "join", listOf("java.lang.String[]"), static = true, returnType = "java.lang.String"),
            ),
            api.classes.getValue("org.example.lib.Box"),
        )
    }

    @Test
    fun `compiler-generated members, nested classes, and non-public classes are left out`() {
        val api =
            model(
                mapOf(
                    "org.example.lib.Outer" to
                        """
                        package org.example.lib;
                        public class Outer implements Comparable<Outer> {
                            public int compareTo(Outer other) { return 0; }
                            public static void access${'$'}000() {}
                            public static class Inner { public void inside() {} }
                        }
                        class Hidden { public void visible() {} }
                        """.trimIndent(),
                ),
            )

        assertEquals(setOf("org.example.lib.Outer"), api.classes.keys)
        val names = api.classes.getValue("org.example.lib.Outer").map { it.name }
        assertEquals(listOf("<init>", "compareTo"), names.sorted(), "no bridge method, no `\$` name")
        assertEquals(
            listOf("org.example.lib.Outer"),
            api.classes
                .getValue("org.example.lib.Outer")
                .single { it.name == "compareTo" }
                .parameters,
        )
    }

    @Test
    fun `a Kotlin file's top-level functions are the static methods of its Kt class`() {
        val stdlib =
            Duration::class.java.protectionDomain.codeSource.location
                .toURI()
                .toPath()

        val api = ApiModel.read(listOf(stdlib))

        val toDuration = api.classes.getValue("kotlin.time.DurationKt").filter { it.name == "toDuration" }
        assertTrue(toDuration.isNotEmpty())
        assertTrue(toDuration.all { it.static })
        assertTrue(listOf("int", "kotlin.time.DurationUnit") in toDuration.map { it.parameters }, "$toDuration")
        assertFalse(api.classes.keys.any { '$' in it })
    }

    @Test
    fun `a JAR that isn't one fails to read`() {
        val broken = dir.resolve("broken.jar").createParentDirectories().apply { writeText("not a zip") }

        assertFailsWith<java.io.IOException> { ApiModel.read(listOf(broken)) }
    }

    @Test
    fun `a member renders like the model reads Java`() {
        val method = ApiMember(MemberKind.METHOD, "join", listOf("java.lang.String[]", "int"), static = true, returnType = "void")
        val constructor = ApiMember(MemberKind.CONSTRUCTOR, "<init>", listOf("int"), static = false, returnType = "void")
        val field = ApiMember(MemberKind.FIELD, "LIMIT", emptyList(), static = true, returnType = "int")

        assertEquals("a.B.join(java.lang.String[], int)", method.render("a.B"))
        assertEquals("a.B(int)", constructor.render("a.B"))
        assertEquals("a.B.LIMIT", field.render("a.B"))
    }
}
