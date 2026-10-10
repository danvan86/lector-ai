package gmbh.vanella.lector.cli

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Recipe
import gmbh.vanella.lector.core.RecipeMatch
import gmbh.vanella.lector.core.RecipeOutcome
import gmbh.vanella.lector.core.RecipeResult
import gmbh.vanella.lector.core.RecipeSelection
import gmbh.vanella.lector.core.UpdateType
import gmbh.vanella.lector.core.VersionChange
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeReportsTest {
    private val change = DependencyChange("maven", "org.example:boot", "3.3.5", "3.4.1", UpdateType.MINOR)
    private val boot = RecipeMatch(Recipe("org.example.UpgradeBoot_3_4", "org.example:recipes:1.0", "Apache License 2.0"), "3.4.x", 1)
    private val cloud = RecipeMatch(Recipe("org.example.UpgradeCloud", "org.example:cloud-recipes:2.0", "MSAL"), "3.4.x", 2)
    private val selection = RecipeSelection(boot, listOf(cloud))

    @Test
    fun `an applied recipe lists itself, its alternatives, the changed files, and every version change`() {
        val outcome =
            RecipeOutcome.Applied(
                changedFiles = listOf("pom.xml", "src/App.java"),
                versionChanges =
                    listOf(
                        VersionChange("org.example:boot", "3.4.1", "3.4.5"),
                        VersionChange("org.example:gone", "1.0", null),
                        VersionChange("org.example:new", null, "2.0"),
                    ),
            )

        assertEquals(
            """
            Recipe (applied)
              Change: org.example:boot 3.3.5 -> 3.4.1 (maven, minor)
              Recipe: org.example.UpgradeBoot_3_4, leaf 3.4.x at depth 1
              Artifact: org.example:recipes:1.0 (Apache License 2.0)
              Alternatives (1):
                - org.example.UpgradeCloud, leaf 3.4.x at depth 2
              Changed files (2):
                - pom.xml
                - src/App.java
              Version changes (3):
                - org.example:boot 3.4.1 -> 3.4.5
                - org.example:gone 1.0 -> removed
                - org.example:new added -> 2.0
            """.trimIndent(),
            RecipeReport.text(change, RecipeResult(selection, outcome)),
        )
    }

    @Test
    fun `an applied recipe that changed no versions and has no alternatives says so`() {
        val outcome = RecipeOutcome.Applied(changedFiles = listOf("src/App.java"), versionChanges = emptyList())

        assertEquals(
            """
            Recipe (applied)
              Change: org.example:boot 3.3.5 -> 3.4.1 (maven, minor)
              Recipe: org.example.UpgradeBoot_3_4, leaf 3.4.x at depth 1
              Artifact: org.example:recipes:1.0 (Apache License 2.0)
              Alternatives (0):
                none
              Changed files (1):
                - src/App.java
              Version changes (0):
                none
            """.trimIndent(),
            RecipeReport.text(change, RecipeResult(RecipeSelection(boot, emptyList()), outcome)),
        )
    }

    @Test
    fun `a recipe that was found but not kept names the recipe and the reason`() {
        assertEquals(
            """
            Recipe (not applied)
              Change: org.example:boot 3.3.5 -> 3.4.1 (maven, minor)
              Reason: the recipe run failed: Maven failed with exit code 1.
                [ERROR] Recipe not found
              Recipe: org.example.UpgradeBoot_3_4, leaf 3.4.x at depth 1
              Artifact: org.example:recipes:1.0 (Apache License 2.0)
              Alternatives (1):
                - org.example.UpgradeCloud, leaf 3.4.x at depth 2
            """.trimIndent(),
            RecipeReport.text(
                change,
                RecipeResult(selection, RecipeOutcome.RunFailed("Maven failed with exit code 1.\n[ERROR] Recipe not found")),
            ),
        )
    }

    @Test
    fun `a downgrade lists the packages that went down`() {
        val downgrade = RecipeOutcome.DowngradeDetected(listOf(VersionChange("org.example:web", "6.2.1", "6.1.0")))

        val text = RecipeReport.text(change, RecipeResult(selection, downgrade))

        assertEquals(
            """
            Recipe (not applied)
              Change: org.example:boot 3.3.5 -> 3.4.1 (maven, minor)
              Reason: the recipe moved packages to lower versions, so its result was discarded
              Recipe: org.example.UpgradeBoot_3_4, leaf 3.4.x at depth 1
              Artifact: org.example:recipes:1.0 (Apache License 2.0)
              Alternatives (1):
                - org.example.UpgradeCloud, leaf 3.4.x at depth 2
              Downgrades (1):
                - org.example:web 6.2.1 -> 6.1.0
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `every reason a recipe doesn't run has its own text`() {
        val reasons =
            mapOf(
                RecipeOutcome.NotAnUpgrade to "3.4.1 is not higher than 3.3.5, so this is not an upgrade",
                RecipeOutcome.PrOutdated("3.4.5", "3.4.1") to
                    "PR outdated: the base already resolves org.example:boot to 3.4.5, higher than 3.4.1",
                RecipeOutcome.NoMatch to "no recipe matches",
                RecipeOutcome.NoRecipeRunner to "the maven ecosystem has no recipe runner",
                RecipeOutcome.VersionsUnreadable("parent not found") to
                    "the resolved versions can't be read, so a downgrade can't be ruled out: parent not found",
            )

        reasons.forEach { (outcome, reason) ->
            val text = RecipeReport.text(change, RecipeResult(RecipeSelection.NONE, outcome))

            assertEquals(
                "Recipe (not applied)\n  Change: org.example:boot 3.3.5 -> 3.4.1 (maven, minor)\n  Reason: $reason",
                text,
            )
        }
    }

    @Test
    fun `json has the change, the outcome, the chosen recipe, and the alternatives`() {
        val outcome = RecipeOutcome.Applied(listOf("pom.xml"), listOf(VersionChange("org.example:boot", "3.4.1", "3.4.5")))

        val json = RecipeReport.json(change, RecipeResult(selection, outcome))

        assertEquals(
            Json.parseToJsonElement(
                """
                {
                  "change": {"ecosystem": "maven", "package": "org.example:boot", "fromVersion": "3.3.5", "toVersion": "3.4.1", "updateType": "minor"},
                  "outcome": "applied",
                  "reason": null,
                  "chosen": {"name": "org.example.UpgradeBoot_3_4", "artifact": "org.example:recipes:1.0", "license": "Apache License 2.0", "selector": "3.4.x", "depth": 1},
                  "alternatives": [{"name": "org.example.UpgradeCloud", "artifact": "org.example:cloud-recipes:2.0", "license": "MSAL", "selector": "3.4.x", "depth": 2}],
                  "changedFiles": ["pom.xml"],
                  "versionChanges": [{"package": "org.example:boot", "before": "3.4.1", "after": "3.4.5"}]
                }
                """,
            ),
            json,
        )
    }

    @Test
    fun `json names every outcome, and lists downgrades as version changes`() {
        val outcomes =
            mapOf(
                RecipeOutcome.NotAnUpgrade to "notAnUpgrade",
                RecipeOutcome.PrOutdated("3.4.5", "3.4.1") to "prOutdated",
                RecipeOutcome.NoMatch to "noMatch",
                RecipeOutcome.NoRecipeRunner to "noRecipeRunner",
                RecipeOutcome.VersionsUnreadable("x") to "versionsUnreadable",
                RecipeOutcome.RunFailed("x") to "runFailed",
                RecipeOutcome.DowngradeDetected(listOf(VersionChange("org.example:web", "6.2.1", "6.1.0"))) to "downgradeDetected",
            )

        outcomes.forEach { (outcome, name) ->
            val json = RecipeReport.json(change, RecipeResult(RecipeSelection.NONE, outcome))

            assertEquals("\"$name\"", json.getValue("outcome").toString())
            assertEquals("null", json.getValue("chosen").toString())
            assertEquals("[]", json.getValue("changedFiles").toString())
        }
        val downgrade = RecipeReport.json(change, RecipeResult(RecipeSelection.NONE, outcomes.keys.last()))
        assertEquals(
            """[{"package":"org.example:web","before":"6.2.1","after":"6.1.0"}]""",
            downgrade.getValue("versionChanges").toString(),
        )
    }
}
