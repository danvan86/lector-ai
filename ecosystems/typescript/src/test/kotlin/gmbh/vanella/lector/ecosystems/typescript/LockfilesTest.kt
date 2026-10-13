package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.ToolError
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The resolved versions of the direct dependencies, from real lockfiles of one workspace: the root depends on
 * `is-odd` 3.0.1 (which pulls in `is-number` 6.0.0), `app` on `is-number` 7.0.0 and on `lib`, `lib` on `is-number` 6.0.0.
 */
class LockfilesTest {
    @TempDir
    lateinit var repo: Path

    private val expected = mapOf("is-number" to "7.0.0", "is-odd" to "3.0.1")

    @Test
    fun `every lockfile format resolves the workspace's direct dependencies, the highest version where they differ`() {
        for (fixture in listOf("npm", "pnpm", "pnpm12", "classic", "berry")) {
            val root = requireNotNull(javaClass.getResource("/lockfiles/$fixture")).toURI().toPath()

            assertEquals(expected, Lockfiles.resolvedVersions(root, NodeProject.read(root)), fixture)
        }
    }

    @Test
    fun `npm resolves a package from the nearest node_modules, the package's own before the root's`() {
        val root = requireNotNull(javaClass.getResource("/lockfiles/npm")).toURI().toPath()
        val app = NodeProject.read(root).packages.filter { it.dir == "packages/app" }

        assertEquals(mapOf("is-number" to "7.0.0"), Lockfiles.resolvedVersions(root, NodeProject(PackageManager.NPM, app)))
    }

    @Test
    fun `a dependency the lockfile doesn't resolve is left out`() {
        file("package.json", """{"dependencies": {"left-pad": "1.3.0", "local": "file:../local"}}""")
        val project = project(PackageManager.NPM)

        file("package-lock.json", """{"lockfileVersion": 3, "packages": {"": {}, "node_modules/local": {"link": true}}}""")
        assertEquals(emptyMap(), Lockfiles.resolvedVersions(repo, project))
        file("package-lock.json", """{"lockfileVersion": 3}""")
        assertEquals(emptyMap(), Lockfiles.resolvedVersions(repo, project))

        file(
            "pnpm-lock.yaml",
            "lockfileVersion: '9.0'\nimporters:\n  .:\n    dependencies:\n      local:\n        version: file:../local\n",
        )
        assertEquals(emptyMap(), Lockfiles.resolvedVersions(repo, project(PackageManager.PNPM)))

        file("yarn.lock", "# yarn lockfile v1\n\n\nother@1.0.0:\n  version \"1.0.0\"\n")
        assertEquals(emptyMap(), Lockfiles.resolvedVersions(repo, project(PackageManager.YARN_CLASSIC)))

        file(
            "yarn.lock",
            "__metadata:\n  version: 10\n\"root@workspace:.\":\n  version: 0.0.0-use.local\n  resolution: \"root@workspace:.\"\n" +
                "  dependencies:\n    left-pad: \"npm:1.3.0\"\n",
        )
        assertEquals(emptyMap(), Lockfiles.resolvedVersions(repo, project(PackageManager.YARN_BERRY)))
    }

    @Test
    fun `pnpm's peer suffixes and aliases are cut off the version`() {
        file("package.json", """{"dependencies": {"ts7": "npm:typescript@7.0.2"}, "devDependencies": {"vitest": "4.1.11"}}""")
        file(
            "pnpm-lock.yaml",
            """
            lockfileVersion: '9.0'
            importers:
              .:
                dependencies:
                  ts7:
                    specifier: npm:typescript@7.0.2
                    version: typescript@7.0.2
                devDependencies:
                  vitest:
                    specifier: 4.1.11
                    version: 4.1.11(@types/node@24.9.1)
            """,
        )

        assertEquals(mapOf("ts7" to "7.0.2", "vitest" to "4.1.11"), Lockfiles.resolvedVersions(repo, project(PackageManager.PNPM)))
    }

    @Test
    fun `a lockfile that can't be read is a tool error`() {
        file("package.json", "{}")
        file("package-lock.json", "[]")

        assertFailsWith<ToolError> { Lockfiles.resolvedVersions(repo, project(PackageManager.NPM)) }
    }

    private fun project(manager: PackageManager) =
        NodeProject(manager, listOf(NodePackage("", PackageJson.read(repo.resolve("package.json"), "package.json"), false)))

    private fun file(
        path: String,
        content: String,
    ) = repo.resolve(path).createParentDirectories().writeText(content.trimIndent())
}
