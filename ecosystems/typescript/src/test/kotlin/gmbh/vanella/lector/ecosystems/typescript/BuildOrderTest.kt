package gmbh.vanella.lector.ecosystems.typescript

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildOrderTest {
    @Test
    fun `only packages another one depends on are built, dependencies first, and only if they have a build script`() {
        val project =
            project(
                pkg("", deps = listOf("app")),
                pkg("packages/app", "app", deps = listOf("lib", "ui", "react")),
                pkg("packages/lib", "lib", deps = listOf("core")),
                pkg("packages/core", "core"),
                pkg("packages/ui", "ui", build = false),
                pkg("packages/docs", "docs", deps = listOf("lib")),
            )

        assertEquals(listOf("packages/core", "packages/lib", "packages/app"), dependencyBuilds(project).map { it.dir })
    }

    @Test
    fun `a cycle is broken where it is found, and a package depending on itself or nameless doesn't count`() {
        val project =
            project(pkg(""), pkg("a", "a", deps = listOf("b", "a")), pkg("b", "b", deps = listOf("a")), pkg("c", null, deps = listOf("a")))

        assertEquals(listOf("b", "a"), dependencyBuilds(project).map { it.dir })
    }

    private fun project(vararg packages: NodePackage) = NodeProject(PackageManager.PNPM, packages.toList())

    private fun pkg(
        dir: String,
        name: String? = dir,
        deps: List<String> = emptyList(),
        build: Boolean = true,
    ) = NodePackage(
        dir,
        PackageJson(
            name,
            deps.associateWith { "workspace:*" },
            if (build) mapOf("build" to "tsc") else emptyMap(),
            emptyList(),
            null,
            null,
        ),
        true,
    )
}
