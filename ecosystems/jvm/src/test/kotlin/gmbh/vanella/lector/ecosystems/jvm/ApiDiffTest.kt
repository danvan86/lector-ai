package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.UpdateType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The typed diff and its text, on models built by hand: the edge cases the scenario tests don't reach. */
class ApiDiffTest {
    private val change = DependencyChange("maven", "org.example:lib", "1.0", "2.0", UpdateType.MAJOR)

    private fun method(
        name: String,
        vararg parameters: String,
        static: Boolean = false,
    ) = ApiMember(MemberKind.METHOD, name, parameters.toList(), static, "void")

    private fun constructor(vararg parameters: String) = ApiMember(MemberKind.CONSTRUCTOR, "<init>", parameters.toList(), false, "void")

    private fun field(
        name: String,
        static: Boolean,
    ) = ApiMember(MemberKind.FIELD, name, emptyList(), static, "int")

    @Test
    fun `the typed diff holds every entry, also beyond the limits of the text`() {
        val old = ApiModel(mapOf("a.Many" to (1..40).map { method("m$it") }.toSet()))
        val new = ApiModel(mapOf("a.Many" to emptySet()))

        val entries = ApiDiff.of(old, new, setOf("a.Many"))

        assertEquals(40, entries.size)
        assertTrue(entries.all { it.kind == ApiChangeKind.REMOVED_MEMBER && !it.unambiguous })
        assertTrue(ApiChangesText.render(change, entries).endsWith("- … and 25 more"))
    }

    @Test
    fun `moved classes, renamed packages, and moved members are unambiguous, rename candidates are not`() {
        assertEquals(
            listOf(ApiChangeKind.RENAMED_PACKAGE, ApiChangeKind.MOVED_CLASS, ApiChangeKind.MOVED_MEMBER),
            ApiChangeKind.entries.filter { it.unambiguous },
        )
    }

    @Test
    fun `a constructor and a static overload show as Java names them`() {
        val old = ApiModel(mapOf("a.Box" to setOf(constructor("int"), method("of", "int", static = true))))
        val new = ApiModel(mapOf("a.Box" to setOf(constructor("int", "int"), method("of", "long", static = true))))

        val text = ApiChangesText.render(change, ApiDiff.of(old, new, setOf("a.Box")))

        assertEquals(
            "API changes of org.example:lib from 1.0 to 2.0\n\nChanged signatures:\n" +
                "- a.Box.of: static (int) → static (long)\n" +
                "- a.Box: (int) → (int, int)",
            text,
        )
        assertFalse('{' in text, "plain text, no JSON")
    }

    @Test
    fun `a static field that moved is a moved member, a field that changed to static is a changed signature`() {
        val old = ApiModel(mapOf("a.Limits" to setOf(field("MAX", static = true), field("size", static = false)), "b.Other" to emptySet()))
        val new = ApiModel(mapOf("a.Limits" to setOf(field("size", static = true)), "b.Other" to setOf(field("MAX", static = true))))

        val text = ApiChangesText.render(change, ApiDiff.of(old, new, setOf("a.Limits")))

        assertTrue("Moved members:\n- a.Limits.MAX → b.Other.MAX" in text, text)
        assertTrue("Changed signatures:\n- a.Limits.size: int → static int" in text, text)
    }

    @Test
    fun `one gone and one new member with other parameters are no rename candidate`() {
        val old = ApiModel(mapOf("a.Greeter" to setOf(method("hello", "java.lang.String"))))
        val new = ApiModel(mapOf("a.Greeter" to setOf(method("greet", "int"))))

        val entries = ApiDiff.of(old, new, setOf("a.Greeter"))

        assertEquals(
            listOf(ApiChange(ApiChangeKind.REMOVED_MEMBER, "a.Greeter", oldMembers = listOf(method("hello", "java.lang.String")))),
            entries,
        )
    }

    @Test
    fun `a static method found in two other classes is removed, not moved`() {
        val old = ApiModel(mapOf("a.Util" to setOf(method("of", "int", static = true))))
        val new =
            ApiModel(
                mapOf(
                    "a.Util" to emptySet(),
                    "b.One" to setOf(method("of", "int", static = true)),
                    "c.Two" to setOf(method("of", "int", static = true)),
                ),
            )

        assertEquals(listOf(ApiChangeKind.REMOVED_MEMBER), ApiDiff.of(old, new, setOf("a.Util")).map { it.kind })
    }

    @Test
    fun `a static member moves only to a static member of the same kind, name, and parameters`() {
        val old = ApiModel(mapOf("a.Util" to setOf(method("of", "int", static = true))))
        val new =
            ApiModel(
                mapOf(
                    "a.Util" to emptySet(),
                    "b.Field" to setOf(ApiMember(MemberKind.FIELD, "of", emptyList(), true, "int")),
                    "c.Instance" to setOf(method("of", "int")),
                    "d.Params" to setOf(method("of", "long", static = true)),
                    "e.Other" to setOf(method("other", "int", static = true)),
                    "f.Target" to setOf(method("of", "int", static = true)),
                ),
            )

        assertEquals(listOf("a.Util.of(int) → f.Target.of(int)"), ApiDiff.of(old, new, setOf("a.Util")).map { it.render() })
    }

    @Test
    fun `classes of a package whose old package lives on, or that moved apart, are no renamed package`() {
        val old =
            ApiModel(mapOf("a.X" to emptySet(), "a.Y" to emptySet(), "a.Kept" to emptySet(), "c.P" to emptySet(), "c.Q" to emptySet()))
        val new =
            ApiModel(mapOf("b.X" to emptySet(), "b.Y" to emptySet(), "a.Kept" to emptySet(), "d.P" to emptySet(), "e.Q" to emptySet()))

        val kinds = ApiDiff.of(old, new, setOf("a.X", "a.Y", "c.P", "c.Q")).map { it.kind }

        assertEquals(List(4) { ApiChangeKind.MOVED_CLASS }, kinds)
    }

    @Test
    fun `a shared limit fills in kind order and counts what is left in the later kind`() {
        val old = ApiModel((1..14).associate { "p$it.Gone$it" to emptySet<ApiMember>() } + ("q.A" to emptySet()) + ("q.B" to emptySet()))
        val new = ApiModel((1..14).associate { "n$it.Gone$it" to emptySet<ApiMember>() })

        val text = ApiChangesText.render(change, ApiDiff.of(old, new, old.classes.keys))

        assertTrue(text.endsWith("Removed classes:\n- q.A\n- … and 1 more"), text)
    }
}
