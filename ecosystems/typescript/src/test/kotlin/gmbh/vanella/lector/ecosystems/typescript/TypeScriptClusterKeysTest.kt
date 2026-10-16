package gmbh.vanella.lector.ecosystems.typescript

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TypeScriptClusterKeysTest {
    private val diagnostics = TscOutput.parse(requireNotNull(javaClass.getResource("/outputs/tsc-noemit.txt")).readText(), ROOT, ROOT)

    @Test
    fun `a missing module groups by module, a missing export by module and symbol, a missing property by property`() {
        assertEquals(
            listOf(
                "export:./b:missing",
                "module:not-a-module",
                "TS1192:src/a.ts",
                "export:./b:Other",
                "TS2322:src/a.ts",
                "property:b",
                "TS2322:src/a.ts",
                "TS2322:src/a.ts",
                "export:./b:presnt",
                "export:./d:Foo",
                "TS2322:src/c.ts",
            ),
            diagnostics.map(TypeScriptClusterKeys::of),
        )
    }

    @Test
    fun `a message that doesn't read as its code expects falls back to code and file`() {
        val odd = diagnostics.first().copy(message = "something else")

        for (code in listOf("TS2307", "TS2305", "TS2339")) assertEquals("$code:src/a.ts", TypeScriptClusterKeys.of(odd.copy(code = code)))
    }

    @Test
    fun `test failures and errors without a code have no key`() {
        assertNull(TypeScriptClusterKeys.of(diagnostics.first().copy(test = "src/a.test.ts#works")))
        assertNull(TypeScriptClusterKeys.of(diagnostics.first().copy(code = null)))
    }

    private companion object {
        val ROOT: Path = Path.of("/work/repo")
    }
}
