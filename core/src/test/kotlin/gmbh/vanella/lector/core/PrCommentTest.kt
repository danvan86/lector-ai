package gmbh.vanella.lector.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PrCommentTest {
    private val boot = DependencyChange("maven", "org.springframework.boot:spring-boot-starter-parent", "3.3.5", "3.4.1", UpdateType.MINOR)
    private val plugin = DependencyChange("maven", "org.springframework.boot:spring-boot-maven-plugin", "3.3.5", "3.4.1", UpdateType.MINOR)
    private val guava = DependencyChange("maven", "com.google.guava:guava", "32.1.3-jre", "33.0.0-jre", UpdateType.MAJOR)
    private val missing =
        Diagnostic("src/main/java/App.java", 12, 9, null, "cannot find symbol\n  symbol: method gone()", Severity.ERROR, "javac")
    private val preExisting =
        Diagnostic(null, null, null, null, "expected 1 but was 2", Severity.ERROR, "surefire", "com.example.AppTest#adds")
    private val flaky = Diagnostic(null, null, null, null, "timed out", Severity.ERROR, "surefire", "com.example.ClockTest#ticks")
    private val unattributed = Diagnostic(null, null, null, null, "boom", Severity.ERROR, "failsafe", "com.example.AppIT#starts")
    private val stub = Violation("stubbing", "src/main/java/App.java", 12, "the edits add the placeholder `return null;` in place of logic")

    private val fullReport =
        PrReport(
            changes = listOf(boot, plugin, guava),
            withoutRecipe = listOf(guava to RecipeOutcome.NoMatch),
            recipes =
                listOf(
                    RecipeApplication(
                        Recipe(
                            "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4",
                            "org.openrewrite.recipe:rewrite-spring:6.0.0",
                            "MSAL",
                        ),
                        listOf(boot, plugin),
                        RecipeOutcome.Applied(listOf("pom.xml"), emptyList()),
                        "1111111aaaaaaa",
                    ),
                ),
            fixLoop =
                FixLoopResult(
                    attempts =
                        listOf(
                            Attempt(
                                "symbol:old",
                                listOf(missing),
                                emptyList(),
                                AttemptOutcome.Accepted("2222222bbbbbbb", "Use new()", Progress(0, 2, 0), Progress(0, 1, 0)),
                            ),
                            Attempt(
                                "symbol:gone",
                                listOf(missing),
                                emptyList(),
                                AttemptOutcome.Rejected("guardrails", listOf(stub, stub)),
                            ),
                            Attempt("symbol:gone", listOf(missing), emptyList(), AttemptOutcome.Rejected("no progress")),
                        ),
                    stopReason = StopReason.NO_PROGRESS,
                    verification =
                        Verification(
                            listOf(missing),
                            listOf(preExisting),
                            listOf(flaky),
                            listOf(unattributed),
                            testsRun = 42,
                        ),
                ),
            pushed = true,
        )

    @Test
    fun `the comment lists recipes, fixes, what is left, what isn't the update's fault, and the guardrail rejections per rule`() {
        assertEquals(
            """
            ### lector-ai

            The update of 3 packages broke the build. lector-ai pushed 2 commits; **1 problem caused by the update is left to fix.**

            #### Recipes
            - `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4` for org.springframework.boot:spring-boot-starter-parent 3.3.5 → 3.4.1, org.springframework.boot:spring-boot-maven-plugin 3.3.5 → 3.4.1: applied in 1111111
            - No recipe for com.google.guava:guava 32.1.3-jre → 33.0.0-jre: no recipe matches

            #### Fixes
            - 2222222 Use new()

            The fix loop made 3 attempts and stopped: no progress in the last attempts.

            #### Left to fix
            - `[javac]` src/main/java/App.java:12: cannot find symbol

            #### Not caused by the update
            Pre-existing (fail on the base commit too):
            - `[surefire]` com.example.AppTest#adds: expected 1 but was 2

            Flaky (passed on a rerun):
            - `[surefire]` com.example.ClockTest#ticks: timed out

            Unattributed (the base commit couldn't tell):
            - `[failsafe]` com.example.AppIT#starts: boom

            #### Guardrail rejections
            - stubbing: 1

            Tests run in the last verify: 42. Review every commit: lector-ai never merges.
            """.trimIndent(),
            PrComment.render(fullReport),
        )
    }

    @Test
    fun `a run that fixed everything and pushed nothing else says so, and leaves empty sections out`() {
        val report =
            PrReport(
                changes = listOf(guava),
                withoutRecipe = listOf(guava to RecipeOutcome.NoMatch),
                recipes = emptyList(),
                fixLoop =
                    FixLoopResult(
                        listOf(
                            Attempt(
                                "symbol:old",
                                listOf(missing),
                                emptyList(),
                                AttemptOutcome.Accepted("3333333", "Use new()", Progress(0, 1, 0), Progress(0, 0, 0)),
                            ),
                        ),
                        StopReason.NOTHING_LEFT,
                        Verification(),
                    ),
                pushed = true,
            )

        val comment = PrComment.render(report)

        assertContains(
            comment,
            "The update of com.google.guava:guava 32.1.3-jre → 33.0.0-jre broke the build. lector-ai pushed 1 commit; nothing caused by the update is left.",
        )
        assertContains(comment, "The fix loop made 1 attempt and stopped: nothing caused by the update is left.")
        listOf("Left to fix", "Not caused by the update", "Guardrail rejections").forEach { assertFalse(it in comment, it) }
        assertContains(comment, "Tests run in the last verify: unknown.")
    }

    @Test
    fun `a run without a commit says that nothing was pushed`() {
        val buildError = Diagnostic(null, null, null, null, "Could not resolve dependencies", Severity.ERROR, "maven")
        val report =
            PrReport(
                changes = listOf(guava),
                withoutRecipe = emptyList(),
                recipes =
                    listOf(
                        RecipeApplication(
                            Recipe("Upgrade", "a", "Apache-2.0"),
                            listOf(guava),
                            RecipeOutcome.RunFailed("Maven failed\nwith details"),
                            null,
                        ),
                        RecipeApplication(
                            Recipe("Noop", "a", "Apache-2.0"),
                            listOf(guava),
                            RecipeOutcome.Applied(emptyList(), emptyList()),
                            null,
                        ),
                    ),
                fixLoop = FixLoopResult(emptyList(), StopReason.ATTEMPT_BUDGET, Verification(listOf(missing, buildError))),
                pushed = false,
            )

        val comment = PrComment.render(report)

        assertContains(comment, "lector-ai pushed nothing; **2 problems caused by the update are left to fix.**")
        assertContains(comment, "- `Upgrade` for com.google.guava:guava 32.1.3-jre → 33.0.0-jre: the recipe run failed: Maven failed\n")
        assertContains(comment, "- `Noop` for com.google.guava:guava 32.1.3-jre → 33.0.0-jre: applied, changed nothing\n")
        assertContains(comment, "#### Fixes\n- none")
        assertContains(comment, "- `[maven]` Could not resolve dependencies")
    }

    @Test
    fun `a long list is cut, with the number left out`() {
        val many = (1..60).map { missing.copy(line = it) }
        val report =
            PrReport(
                listOf(guava),
                emptyList(),
                emptyList(),
                FixLoopResult(emptyList(), StopReason.ATTEMPT_BUDGET, Verification(many)),
                false,
            )

        val comment = PrComment.render(report)

        assertContains(comment, "src/main/java/App.java:50: cannot find symbol\n- and 10 more")
        assertFalse("App.java:51:" in comment)
    }

    @Test
    fun `many changes without a recipe are cut to the first ten, with the number left out, and a missing version shows as none`() {
        val added = guava.copy(`package` = "org.example:new", fromVersion = "")
        val removed = guava.copy(`package` = "org.example:gone", toVersion = "")
        val transitive = (1..12).map { guava.copy(`package` = "org.example:lib$it") }
        val changes = listOf(added, removed) + transitive
        val report =
            PrReport(
                changes,
                changes.map { it to RecipeOutcome.NoMatch },
                emptyList(),
                FixLoopResult(emptyList(), StopReason.ATTEMPT_BUDGET, Verification(listOf(missing))),
                false,
            )

        val comment = PrComment.render(report)

        assertContains(comment, "The update of 14 packages broke the build.")
        assertContains(comment, "- No recipe for org.example:new none → 33.0.0-jre: no recipe matches\n")
        assertContains(comment, "- No recipe for org.example:gone 32.1.3-jre → none: no recipe matches\n")
        assertContains(
            comment,
            "- No recipe for org.example:lib8 32.1.3-jre → 33.0.0-jre: no recipe matches\n- and 4 more changes without a recipe",
        )
        assertFalse("org.example:lib9 " in comment)
    }

    @Test
    fun `a green run names the commit and lists only what isn't the update's fault`() {
        assertEquals(
            """
            ### lector-ai

            Verify is green on 4444444: nothing caused by the update. lector-ai changed nothing.
            """.trimIndent(),
            PrComment.green(Verification(), "4444444cccccccc"),
        )
        assertContains(
            PrComment.green(Verification(flaky = listOf(flaky)), "4444444"),
            "Flaky (passed on a rerun):\n- `[surefire]` com.example.ClockTest#ticks",
        )
    }
}
