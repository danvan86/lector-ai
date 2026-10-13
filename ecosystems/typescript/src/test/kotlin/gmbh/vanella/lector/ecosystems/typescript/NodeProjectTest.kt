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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Detection, the package manager, the workspace packages, and the repositories the adapter refuses. */
class NodeProjectTest {
    @TempDir
    lateinit var repo: Path

    @Test
    fun `a repository is TypeScript with a package_json and a tsconfig_json at the root`() {
        assertFalse(NodeProject.detect(repo))
        file("package.json", "{}")
        assertFalse(NodeProject.detect(repo), "without a tsconfig.json")
        file("tsconfig.json", "{}")
        assertTrue(NodeProject.detect(repo))
        file("package.json").toFile().delete()
        assertFalse(NodeProject.detect(repo), "without a package.json")
    }

    @Test
    fun `the package manager comes from the lockfile, Yarn classic and Berry from its format`() {
        assertEquals(PackageManager.NPM, read("npm").manager)
        assertEquals(PackageManager.PNPM, read("pnpm").manager)
        assertEquals(PackageManager.PNPM, read("pnpm12").manager)
        assertEquals(PackageManager.YARN_CLASSIC, read("classic").manager)
        assertEquals(PackageManager.YARN_BERRY, read("berry").manager)
    }

    @Test
    fun `workspace packages come from package_json, or for pnpm from pnpm-workspace_yaml`() {
        for (fixture in listOf("npm", "pnpm", "classic", "berry")) {
            val project = read(fixture)

            assertEquals(listOf("", "packages/app", "packages/lib"), project.packages.map { it.dir }, fixture)
            assertEquals(listOf("lock-root", "@lock/app", "@lock/lib"), project.packages.map { it.manifest.name }, fixture)
        }
    }

    @Test
    fun `workspace globs follow npm's rules, negations and nested directories included, never into node_modules`() {
        file("package.json", """{"workspaces": {"packages": ["apps/*", "libs/**", "!libs/old"]}}""")
        file("package-lock.json", """{"lockfileVersion": 3}""")
        for (dir in listOf("apps/web", "apps/api", "libs/a", "libs/a/nested", "libs/old", "apps/web/node_modules/dep")) {
            file("$dir/package.json", """{"name": "${dir.replace('/', '-')}"}""")
        }
        file("apps/docs/README.md", "no package")

        assertEquals(listOf("", "apps/api", "apps/web", "libs/a", "libs/a/nested"), NodeProject.read(repo).packages.map { it.dir })
    }

    @Test
    fun `a package's dependencies of every kind, its scripts, and whether it has a tsconfig_json`() {
        file(
            "package.json",
            """
            {
              "name": "root",
              "scripts": {"build": "tsc", "test": "jest"},
              "dependencies": {"a": "1.0.0"},
              "devDependencies": {"b": "^2.0.0", "a": "9.9.9", "typescript": "5.9.3"},
              "optionalDependencies": {"c": "~3.0.0"},
              "peerDependencies": {"d": "*"},
              "engines": {"node": ">=22"},
              "packageManager": "npm@11.6.2"
            }
            """,
        )
        file("tsconfig.json", "{}")
        file("package-lock.json", """{"lockfileVersion": 3}""")

        val root = NodeProject.read(repo).packages.single()

        assertEquals(mapOf("c" to "~3.0.0", "b" to "^2.0.0", "a" to "1.0.0", "typescript" to "5.9.3"), root.manifest.dependencies)
        assertEquals(mapOf("build" to "tsc", "test" to "jest"), root.manifest.scripts)
        assertEquals(">=22", root.manifest.engines)
        assertEquals("npm@11.6.2", root.manifest.packageManager)
        assertTrue(root.hasTsconfig)
    }

    @Test
    fun `no lockfile is a tool error`() {
        file("package.json", "{}")

        assertError(
            "$repo has no lockfile. lector-ai installs exactly what the lockfile says: commit a package-lock.json, pnpm-lock.yaml, or yarn.lock.",
        )
    }

    @Test
    fun `more than one lockfile is a tool error`() {
        file("package.json", "{}")
        file("package-lock.json", """{"lockfileVersion": 3}""")
        file("yarn.lock", "# yarn lockfile v1\n")

        assertError(
            "$repo has more than one lockfile (package-lock.json, yarn.lock). lector-ai doesn't pick a package manager: remove all but one.",
        )
    }

    @Test
    fun `an npm lockfile older than version 2 is a tool error that names the format`() {
        file("package.json", "{}")
        file("package-lock.json", """{"lockfileVersion": 1}""")

        assertError(
            "package-lock.json has lockfileVersion 1, written by npm 6 or older. lector-ai supports lockfileVersion 2 and 3 (npm 7 and newer).",
        )
    }

    @Test
    fun `a pnpm lockfile older than version 9 is a tool error that names the format`() {
        file("package.json", "{}")
        file("pnpm-lock.yaml", "lockfileVersion: '6.0'\n")

        assertError(
            "pnpm-lock.yaml has lockfileVersion 6.0, written by pnpm 8 or older. lector-ai supports lockfileVersion 9.0 (pnpm 9 and newer).",
        )
    }

    @Test
    fun `a Yarn Berry lockfile older than Yarn 4 is a tool error that names the format`() {
        file("package.json", """{"packageManager": "yarn@3.8.7"}""")
        file("yarn.lock", "__metadata:\n  version: 6\n  cacheKey: 8\n")

        assertError("yarn.lock has __metadata.version 6, written by Yarn 3 or older. lector-ai supports version 8 and newer (Yarn 4).")
    }

    @Test
    fun `a lockfile that can't be read is a tool error`() {
        file("package.json", "{}")
        file("package-lock.json", "{not json")
        assertError("Can't read package-lock.json: ", prefix = true)

        file("package-lock.json").toFile().delete()
        file("pnpm-lock.yaml", "lockfileVersion: [")
        assertError("Can't read pnpm-lock.yaml: ", prefix = true)

        file("pnpm-lock.yaml").toFile().delete()
        file("yarn.lock", "neither yarn format\n")
        assertError("Can't read yarn.lock: it is neither Yarn classic's format (`# yarn lockfile v1`) nor Yarn Berry's (`__metadata`)")
    }

    @Test
    fun `a Yarn Berry lockfile without a pinned Yarn is a tool error`() {
        file("package.json", "{}")
        file("yarn.lock", "__metadata:\n  version: 8\n")

        assertError(
            "yarn.lock is Yarn Berry's, but package.json names no packageManager and .yarnrc.yml no yarnPath. " +
                "lector-ai doesn't guess the Yarn version: add \"packageManager\": \"yarn@<version>\" to package.json.",
        )

        file(".yarnrc.yml", "yarnPath: .yarn/releases/yarn-4.18.1.cjs\n")
        assertEquals(PackageManager.YARN_BERRY, NodeProject.read(repo).manager)
    }

    @Test
    fun `a package_json that isn't a JSON object is a tool error`() {
        file("package.json", "[]")
        file("package-lock.json", """{"lockfileVersion": 3}""")

        assertError("Can't read package.json: it isn't a JSON object")

        file("package.json", "{")
        assertError("Can't read package.json: ", prefix = true)
    }

    @Test
    fun `a package with a tsconfig_json needs typescript, from itself or the root`() {
        file("package.json", """{"workspaces": ["packages/*"]}""")
        file("package-lock.json", """{"lockfileVersion": 3}""")
        file("tsconfig.json", "{}")
        file("packages/app/package.json", """{"name": "app", "devDependencies": {"typescript": "5.9.3"}}""")
        file("packages/app/tsconfig.json", "{}")
        file("packages/docs/package.json", """{"name": "docs"}""")

        assertError(
            "The root package has a tsconfig.json, but neither it nor the root package.json depends on typescript. " +
                "lector-ai type-checks with the project's own tsc.",
        )

        file("package.json", """{"workspaces": ["packages/*"], "devDependencies": {"typescript": "7.0.2"}}""")
        file("packages/app/package.json", """{"name": "app"}""")
        NodeProject.read(repo)

        file("package.json", """{"workspaces": ["packages/*"]}""")
        file("tsconfig.json").toFile().delete()
        assertError(
            "packages/app has a tsconfig.json, but neither it nor the root package.json depends on typescript. " +
                "lector-ai type-checks with the project's own tsc.",
        )
    }

    private fun read(fixture: String) = NodeProject.read(requireNotNull(javaClass.getResource("/lockfiles/$fixture")).toURI().toPath())

    private fun file(
        path: String,
        content: String? = null,
    ): Path {
        val file = repo.resolve(path)
        if (content != null) file.createParentDirectories().writeText(content.trimIndent())
        return file
    }

    private fun assertError(
        message: String,
        prefix: Boolean = false,
    ) {
        val error = assertFailsWith<ToolError> { NodeProject.read(repo) }
        if (prefix) assertTrue(error.message.startsWith(message), error.message) else assertEquals(message, error.message)
    }

    @Test
    fun `values that aren't what package_json says they are, are left out`() {
        file(
            "package.json",
            """{"name": {}, "scripts": {"test": 1, "build": "tsc"}, "dependencies": "x", "workspaces": {"nohoist": []}, "engines": "x"}""",
        )
        file("package-lock.json", """{"lockfileVersion": 3}""")

        val root = NodeProject.read(repo).packages.single()

        assertEquals(PackageJson(null, emptyMap(), mapOf("build" to "tsc"), emptyList(), null, null), root.manifest)
    }

    @Test
    fun `lockfiles without a format version are read as the oldest`() {
        file("package.json", "{}")
        file("package-lock.json", "{}")
        assertError(
            "package-lock.json has lockfileVersion 1, written by npm 6 or older. lector-ai supports lockfileVersion 2 and 3 (npm 7 and newer).",
        )
        file("package-lock.json", """{"lockfileVersion": "three"}""")
        assertError("package-lock.json has lockfileVersion 1, ", prefix = true)

        file("package-lock.json").toFile().delete()
        file("pnpm-lock.yaml", "lockfileVersion: x\n")
        assertError(
            "pnpm-lock.yaml has lockfileVersion x, written by pnpm 8 or older. lector-ai supports lockfileVersion 9.0 (pnpm 9 and newer).",
        )

        file("pnpm-lock.yaml").toFile().delete()
        file("yarn.lock", "__metadata:\n  cacheKey: 8\n")
        assertError("yarn.lock has __metadata.version 0, written by Yarn 3 or older. lector-ai supports version 8 and newer (Yarn 4).")

        file("yarn.lock", "key: [\n")
        assertError("Can't read yarn.lock: it is neither Yarn classic's format (`# yarn lockfile v1`) nor Yarn Berry's (`__metadata`)")
    }

    @Test
    fun `Yarn Berry needs packageManager to name Yarn, or a yarnPath`() {
        file("package.json", """{"packageManager": "pnpm@10.34.6"}""")
        file("yarn.lock", "__metadata:\n  version: 10\n")
        file(".yarnrc.yml", "nodeLinker: node-modules\n")

        assertError("yarn.lock is Yarn Berry's, but package.json names no packageManager and .yarnrc.yml no yarnPath. ", prefix = true)
    }

    @Test
    fun `pnpm without workspace globs has only the root package`() {
        file("package.json", """{"workspaces": ["packages/*"]}""")
        file("pnpm-lock.yaml", "lockfileVersion: '9.0'\n")
        file("packages/app/package.json", "{}")

        assertEquals(listOf(""), NodeProject.read(repo).packages.map { it.dir }, "pnpm ignores package.json's workspaces")

        file("pnpm-workspace.yaml", "onlyBuiltDependencies: [esbuild]\n")
        assertEquals(listOf(""), NodeProject.read(repo).packages.map { it.dir })

        file("pnpm-workspace.yaml", "packages: [packages/*]\n")
        file("tools/cli/package.json", "{}")
        assertEquals(listOf("", "packages/app"), NodeProject.read(repo).packages.map { it.dir })
    }
}
