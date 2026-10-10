package gmbh.vanella.lector.recipes

import kotlin.test.Test
import kotlin.test.assertEquals

class IndexReportTest {
    @Test
    fun `renders counts, skipped leaves by reason, and the license of every recipe artifact`() {
        val report =
            IndexReport(
                recipes = 2,
                entries = 3,
                skipped = mapOf("SetRange" to 1, "LatestRelease" to 4),
                artifacts =
                    mapOf(
                        RecipeArtifact("org.openrewrite:rewrite-maven:8.89.0", "Apache License 2.0") to 0,
                        RecipeArtifact("org.openrewrite.recipe:rewrite-spring:6.37.0", "Moderne Source Available License") to 2,
                    ),
            )

        assertEquals(
            """
            # Recipe index report

            Generated from `org.openrewrite.recipe:rewrite-recipe-bom:3.37.0`.

            - Indexed recipes: 2
            - Index entries: 3
            - Skipped leaves: 5

            ## Skipped leaves by reason

            | Reason | Leaves |
            |---|---|
            | LatestRelease | 4 |
            | SetRange | 1 |

            ## Recipe artifacts

            | Artifact | License | Indexed recipes |
            |---|---|---|
            | org.openrewrite.recipe:rewrite-spring:6.37.0 | Moderne Source Available License | 2 |
            | org.openrewrite:rewrite-maven:8.89.0 | Apache License 2.0 | 0 |
            
            """.trimIndent(),
            report.render("org.openrewrite.recipe:rewrite-recipe-bom:3.37.0"),
        )
    }

    @Test
    fun `no skipped leaves is said, not left as an empty table`() {
        val report = IndexReport(recipes = 0, entries = 0, skipped = emptyMap(), artifacts = emptyMap())

        val text = report.render("bom")

        assertEquals(true, "## Skipped leaves by reason\n\nNone.\n" in text, text)
    }
}
