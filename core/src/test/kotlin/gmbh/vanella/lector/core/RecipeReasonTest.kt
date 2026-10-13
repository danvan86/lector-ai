package gmbh.vanella.lector.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeReasonTest {
    private val change = DependencyChange("maven", "org.example:boot", "3.3.5", "3.4.1", UpdateType.MINOR)

    @Test
    fun `every outcome but an applied recipe says why there is no result`() {
        val reasons =
            mapOf(
                RecipeOutcome.Applied(listOf("pom.xml"), emptyList()) to null,
                RecipeOutcome.NotAnUpgrade to "3.4.1 is not higher than 3.3.5, so this is not an upgrade",
                RecipeOutcome.PrOutdated("3.5.0", "3.4.1") to
                    "PR outdated: the base already resolves org.example:boot to 3.5.0, higher than 3.4.1",
                RecipeOutcome.NoMatch to "no recipe matches",
                RecipeOutcome.NoRecipeRunner to "the maven ecosystem has no recipe runner",
                RecipeOutcome.VersionsUnreadable("no POM") to
                    "the resolved versions can't be read, so a downgrade can't be ruled out: no POM",
                RecipeOutcome.RunFailed("boom") to "the recipe run failed: boom",
                RecipeOutcome.DowngradeDetected(emptyList()) to "the recipe moved packages to lower versions, so its result was discarded",
            )

        reasons.forEach { (outcome, reason) -> assertEquals(reason, outcome.reason(change), outcome.toString()) }
    }

    @Test
    fun `a new or a removed package says so`() {
        assertEquals("the package is new, so this is not an upgrade", RecipeOutcome.NotAnUpgrade.reason(change.copy(fromVersion = "")))
        assertEquals("the package is gone, so this is not an upgrade", RecipeOutcome.NotAnUpgrade.reason(change.copy(toVersion = "")))
    }
}
