package gmbh.vanella.lector.ecosystems.typescript

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestScriptsTest {
    @Test
    fun `finds Jest and Vitest, with the script's arguments`() {
        assertEquals(TestCommand(Runner.JEST, emptyMap(), emptyList()), TestScripts.command("jest"))
        assertEquals(
            TestCommand(Runner.JEST, emptyMap(), listOf("--config", "jest.unit.js")),
            TestScripts.command("jest --config jest.unit.js"),
        )
        assertEquals(TestCommand(Runner.VITEST, emptyMap(), emptyList()), TestScripts.command("vitest run"))
        assertEquals(
            TestCommand(Runner.VITEST, emptyMap(), listOf("--project", "unit")),
            TestScripts.command("vitest --run --project unit"),
        )
        assertEquals(TestCommand(Runner.VITEST, emptyMap(), listOf("--root", "run")), TestScripts.command("vitest --root run"))
    }

    @Test
    fun `behind launchers, variables, and other commands`() {
        assertEquals(TestCommand(Runner.JEST, emptyMap(), emptyList()), TestScripts.command("npx --no-install jest"))
        assertEquals(TestCommand(Runner.VITEST, emptyMap(), emptyList()), TestScripts.command("pnpm exec vitest"))
        assertEquals(TestCommand(Runner.JEST, emptyMap(), emptyList()), TestScripts.command("yarn jest"))
        assertEquals(TestCommand(Runner.JEST, emptyMap(), emptyList()), TestScripts.command("npm exec -- jest"))
        assertEquals(
            TestCommand(Runner.JEST, mapOf("NODE_OPTIONS" to "--experimental-vm-modules", "TZ" to "UTC"), listOf("--silent")),
            TestScripts.command("cross-env NODE_OPTIONS='--experimental-vm-modules' TZ=UTC jest --silent"),
        )
        assertEquals(TestCommand(Runner.JEST, emptyMap(), listOf("-t", "a b")), TestScripts.command("tsc --noEmit && jest -t \"a b\""))
        assertEquals(TestCommand(Runner.VITEST, emptyMap(), emptyList()), TestScripts.command("npm run lint ; vitest || exit 1"))
    }

    @Test
    fun `any other test script names no runner`() {
        assertNull(TestScripts.command("node --test"))
        assertNull(TestScripts.command("mocha 'test/**/*.ts'"))
        assertNull(TestScripts.command("echo jest"))
        assertNull(TestScripts.command(""))
        assertNull(TestScripts.command("  "))
    }
}
