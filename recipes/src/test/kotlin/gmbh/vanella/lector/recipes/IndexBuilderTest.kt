package gmbh.vanella.lector.recipes

import kotlin.test.Test
import kotlin.test.assertEquals

class IndexBuilderTest {
    private val spring = RecipeArtifact("org.openrewrite.recipe:rewrite-spring:6.37.0", "Moderne Source Available License")
    private val core = RecipeArtifact("org.openrewrite:rewrite-maven:8.89.0", "Apache License 2.0")

    // Stands in for OpenRewrite's own selector check: exact versions and X-ranges are supported.
    private val builder =
        IndexBuilder { selector, versionPattern ->
            when {
                versionPattern != null -> "ExactVersionWithPattern"
                selector.startsWith("latest.") -> "LatestRelease"
                selector.startsWith("[") -> "SetRange"
                else -> null
            }
        }

    @Test
    fun `reads all three leaf types, at any depth, in both dependency variants`() {
        val recipe =
            recipe(
                "UpgradeSpringBoot_3_4",
                leaf(PARENT, "org.springframework.boot", "spring-boot-starter-parent", "3.4.x"),
                leaf(PLUGIN, "org.springframework.boot", "spring-boot-maven-plugin", "3.4.x"),
                node("UpgradeSpringFramework_6_2", leaf(JAVA_DEPENDENCY, "org.springframework", "*", "6.2.x")),
                node("Nested", node("Deeper", leaf(MAVEN_DEPENDENCY, "org.flywaydb", "flyway-core", "10.20.1"))),
            )

        val index = builder.build(listOf(ScannedRecipe(recipe, spring)))

        assertEquals(
            listOf(
                entry("org.springframework.boot:spring-boot-starter-parent", "3.4.x", 1),
                entry("org.springframework.boot:spring-boot-maven-plugin", "3.4.x", 1),
                entry("org.springframework:*", "6.2.x", 2),
                entry("org.flywaydb:flyway-core", "10.20.1", 3),
            ),
            index.entries,
        )
    }

    @Test
    fun `other recipe types produce no entry`() {
        val recipe =
            recipe(
                "UpgradeSpringBoot_3_4",
                RecipeTree(
                    "org.openrewrite.java.dependencies.ChangeDependency",
                    mapOf("oldGroupId" to "org.webjars", "oldArtifactId" to "webjars-locator-core", "newVersion" to "1.0.x"),
                    emptyList(),
                ),
                RecipeTree(
                    "org.openrewrite.gradle.UpgradeDependencyVersion",
                    mapOf("groupId" to "org.example", "newVersion" to "3.4.x"),
                    emptyList(),
                ),
            )

        val index = builder.build(listOf(ScannedRecipe(recipe, spring)))

        assertEquals(emptyList(), index.entries)
        assertEquals(IndexReport(recipes = 0, entries = 0, skipped = emptyMap(), artifacts = mapOf(spring to 0)), index.report)
    }

    @Test
    fun `a Gradle plugin leaf is an entry for the plugin's marker coordinates`() {
        val recipe =
            recipe(
                "UpgradeSpringBoot_3_4",
                RecipeTree(GRADLE_PLUGIN, mapOf("pluginIdPattern" to "org.springframework.boot", "newVersion" to "3.4.x"), emptyList()),
                node(
                    "UpgradeKotlin",
                    RecipeTree(GRADLE_PLUGIN, mapOf("pluginIdPattern" to "org.jetbrains.kotlin.*", "newVersion" to "2.1.x"), emptyList()),
                ),
            )

        val index = builder.build(listOf(ScannedRecipe(recipe, spring)))

        assertEquals(
            listOf(
                entry("org.springframework.boot:org.springframework.boot.gradle.plugin", "3.4.x", 1),
                entry("org.jetbrains.kotlin.*:org.jetbrains.kotlin.*.gradle.plugin", "2.1.x", 2),
            ),
            index.entries,
        )
    }

    @Test
    fun `a Gradle plugin leaf without a plugin ID is skipped`() {
        val recipe = recipe("Broken", RecipeTree(GRADLE_PLUGIN, mapOf("newVersion" to "3.4.x"), emptyList()))

        val index = builder.build(listOf(ScannedRecipe(recipe, spring)))

        assertEquals(emptyList(), index.entries)
        assertEquals(mapOf("missing option" to 1), index.report.skipped)
    }

    @Test
    fun `a recipe is not its own leaf`() {
        val index = builder.build(listOf(ScannedRecipe(leaf(PARENT, "org.example", "parent", "1.0.x"), core)))

        assertEquals(emptyList(), index.entries)
    }

    @Test
    fun `unsupported selectors are skipped and counted by reason`() {
        val recipe =
            recipe(
                "UpgradeAll",
                leaf(MAVEN_DEPENDENCY, "org.example", "a", "latest.release"),
                leaf(MAVEN_DEPENDENCY, "org.example", "b", "latest.release"),
                leaf(MAVEN_DEPENDENCY, "org.example", "c", "[3.5.0,4.0.0)"),
                leaf(MAVEN_DEPENDENCY, "org.example", "d", "2.47.6-01", versionPattern = "-jre"),
                leaf(MAVEN_DEPENDENCY, "org.example", "e", "1.2.x"),
            )

        val index = builder.build(listOf(ScannedRecipe(recipe, core)))

        assertEquals(listOf(entry("org.example:e", "1.2.x", 1, recipe = "UpgradeAll", artifact = core)), index.entries)
        assertEquals(mapOf("LatestRelease" to 2, "SetRange" to 1, "ExactVersionWithPattern" to 1), index.report.skipped)
    }

    @Test
    fun `a leaf without a package or a version is skipped`() {
        val recipe =
            recipe(
                "Broken",
                RecipeTree(MAVEN_DEPENDENCY, mapOf("groupId" to "org.example", "newVersion" to "1.x"), emptyList()),
                RecipeTree(PARENT, mapOf("groupId" to "org.example", "artifactId" to "parent", "newVersion" to null), emptyList()),
                RecipeTree(PLUGIN, mapOf("artifactId" to "plugin", "newVersion" to "1.x"), emptyList()),
            )

        val index = builder.build(listOf(ScannedRecipe(recipe, core)))

        assertEquals(emptyList(), index.entries)
        assertEquals(mapOf("missing option" to 3), index.report.skipped)
    }

    @Test
    fun `the same leaf twice in one recipe is one entry, at its shallowest depth`() {
        val recipe =
            recipe(
                "UpgradeSpringBoot_3_4",
                node("UpgradeSpringDoc_2_8", leaf(JAVA_DEPENDENCY, "org.springdoc", "*", "2.8.x")),
                leaf(JAVA_DEPENDENCY, "org.springdoc", "*", "2.8.x"),
            )

        val index = builder.build(listOf(ScannedRecipe(recipe, spring)))

        assertEquals(listOf(entry("org.springdoc:*", "2.8.x", 1)), index.entries)
    }

    @Test
    fun `the report counts recipes, entries, and recipes per artifact with its license`() {
        val boot = recipe("UpgradeSpringBoot_3_4", leaf(PARENT, "org.springframework.boot", "spring-boot-starter-parent", "3.4.x"))
        val bootOld = recipe("UpgradeSpringBoot_3_3", leaf(PARENT, "org.springframework.boot", "spring-boot-starter-parent", "3.3.x"))
        val unrelated = recipe("org.openrewrite.maven.RemoveRedundantDependencyVersions")

        val index = builder.build(listOf(ScannedRecipe(boot, spring), ScannedRecipe(bootOld, spring), ScannedRecipe(unrelated, core)))

        assertEquals(IndexReport(recipes = 2, entries = 2, skipped = emptyMap(), artifacts = mapOf(spring to 2, core to 0)), index.report)
    }

    @Test
    fun `entries are sorted by recipe, so the generated index is stable`() {
        val b = recipe("b.Upgrade", leaf(PARENT, "org.example", "parent", "2.x"))
        val a = recipe("a.Upgrade", leaf(PARENT, "org.example", "parent", "1.x"))

        val index = builder.build(listOf(ScannedRecipe(b, core), ScannedRecipe(a, core)))

        assertEquals(listOf("a.Upgrade", "b.Upgrade"), index.entries.map { it.recipe })
    }

    private fun recipe(
        name: String,
        vararg children: RecipeTree,
    ) = RecipeTree(name, emptyMap(), children.toList())

    private fun node(
        name: String,
        vararg children: RecipeTree,
    ) = recipe(name, *children)

    private fun leaf(
        type: String,
        groupId: String,
        artifactId: String,
        newVersion: String,
        versionPattern: String? = null,
    ) = RecipeTree(
        type,
        mapOf("groupId" to groupId, "artifactId" to artifactId, "newVersion" to newVersion, "versionPattern" to versionPattern),
        emptyList(),
    )

    private fun entry(
        pattern: String,
        selector: String,
        depth: Int,
        recipe: String = "UpgradeSpringBoot_3_4",
        artifact: RecipeArtifact = spring,
    ) = IndexEntry("maven", pattern, selector, recipe, depth, artifact.coordinates, artifact.license)

    private companion object {
        const val MAVEN_DEPENDENCY = "org.openrewrite.maven.UpgradeDependencyVersion"
        const val JAVA_DEPENDENCY = "org.openrewrite.java.dependencies.UpgradeDependencyVersion"
        const val PARENT = "org.openrewrite.maven.UpgradeParentVersion"
        const val PLUGIN = "org.openrewrite.maven.UpgradePluginVersion"
        const val GRADLE_PLUGIN = "org.openrewrite.gradle.plugins.UpgradePluginVersion"
    }
}
