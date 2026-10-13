package gmbh.vanella.lector.sources

import gmbh.vanella.lector.core.BranchPolicy
import gmbh.vanella.lector.core.PackageUpdate
import gmbh.vanella.lector.core.PullRequest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reads PR bodies that Renovate wrote, captured from public repositories (`src/test/resources/renovate/`). */
class RenovateSourceTest {
    private val source = RenovateSource(setOf("renovate[bot]", "dependabot[bot]"))

    @Test
    fun `recognizes a PR by a bot login and the renovate branch prefix`() {
        assertTrue(source.recognizes(pr(branch = "renovate/spring-boot")))
        assertFalse(source.recognizes(pr(branch = "dependabot/maven/org.springframework.boot-3.4.0")))
        assertFalse(source.recognizes(pr(branch = "feature/renovate/x")))
        assertFalse(source.recognizes(pr(branch = "renovate/spring-boot").copy(author = "mallory")))
    }

    @Test
    fun `is called renovate and fixes in place`() {
        assertEquals("renovate", source.name)
        assertEquals(BranchPolicy.IN_PLACE, source.branchPolicy)
    }

    @Test
    fun `reads a single update with its ecosystem from the merge-confidence badges`() {
        val update = source.read(pr(body = captured("single-update.md")), NoContext)

        assertEquals(
            listOf(PackageUpdate("maven", "org.springframework.boot:spring-boot-starter-parent", "4.0.3", "4.1.1")),
            update.updates,
        )
    }

    @Test
    fun `reads every change of a monorepo group, in the order of the table`() {
        val update = source.read(pr(body = captured("monorepo-group.md")), NoContext)

        assertEquals(
            listOf(
                PackageUpdate("maven", "org.springframework.boot:spring-boot-maven-plugin", "3.1.4", "3.5.16"),
                PackageUpdate("maven", "org.springframework.boot:spring-boot-dependencies", "3.1.4", "3.5.16"),
            ),
            update.updates,
        )
    }

    @Test
    fun `reads every row of a large group`() {
        val update = source.read(pr(body = captured("maven-group.md")), NoContext)

        assertEquals(13, update.updates.size)
        assertEquals(PackageUpdate("maven", "de.thetaphi:forbiddenapis", "3.10", "3.11"), update.updates.first())
        assertEquals(PackageUpdate("maven", "com.google.guava:guava", "33.7.1-jre", "33.7.2-jre"), update.updates[4])
        assertEquals(
            PackageUpdate("maven", "org.sonarsource.java:sonar-java-plugin", "8.41.0.47177", "8.44.0.48651"),
            update.updates.last(),
        )
    }

    @Test
    fun `a Gradle plugin row is read as the plugin's marker coordinates from its badges`() {
        val update = source.read(pr(body = captured("gradle-spring-boot-plugin.md")), NoContext)

        assertEquals(
            listOf(
                PackageUpdate("maven", "org.springframework.boot:org.springframework.boot.gradle.plugin", "4.0.5", "4.1.1"),
                PackageUpdate("maven", "org.springframework.boot:spring-boot-gradle-plugin", "4.0.5", "4.1.1"),
            ),
            update.updates,
        )
    }

    @Test
    fun `a version catalog row is read by its package, not by the alias the table shows, and the wrapper row is left out`() {
        val update = source.read(pr(body = captured("gradle-catalog-alias.md")), NoContext)

        assertEquals(
            listOf(
                PackageUpdate("maven", "org.jetbrains.kotlin:kotlin-gradle-plugin", "2.4.20", "2.4.21"),
                PackageUpdate("maven", "ch.qos.logback:logback-classic", "1.6.4", "1.6.5"),
            ),
            update.updates,
        )
    }

    @Test
    fun `a Gradle wrapper PR without badges has no version changes`() {
        assertEquals(emptyList<PackageUpdate>(), source.read(pr(body = captured("gradle-wrapper.md")), NoContext).updates)
    }

    @Test
    fun `extracts the release notes from the body, without Renovate's configuration and footer`() {
        val notes = assertNotNull(source.read(pr(body = captured("single-update.md")), NoContext).releaseNotes)

        assertTrue(
            notes.startsWith(
                "<details>\n<summary>spring-projects/spring-boot (org.springframework.boot:spring-boot-starter-parent)</summary>",
            ),
        )
        assertContains(notes, "Kafka consumer-specific security protocol is not taken into account [#51369]")
        assertTrue(notes.endsWith("</details>"), notes.takeLast(100))
        assertFalse("### Configuration" in notes)
        assertFalse("rebase-check" in notes)
        assertFalse("&#8203;" in notes, "zero-width spaces are decoded away")
    }

    @Test
    fun `a body without release notes has none`() {
        assertNull(source.read(pr(body = captured("digest.md")), NoContext).releaseNotes)
    }

    @Test
    fun `digest and pin digest rows are no version changes`() {
        listOf("digest.md", "pin-digest.md").forEach { body ->
            assertEquals(emptyList<PackageUpdate>(), source.read(pr(body = captured(body)), NoContext).updates, body)
        }
    }

    @Test
    fun `pin rows are no version changes, the patch updates in the same PR are`() {
        val update = source.read(pr(body = captured("pin.md")), NoContext)

        assertEquals(
            listOf(
                PackageUpdate("npm", "convex-test", "0.0.55", "0.0.60"),
                PackageUpdate("npm", "prettier", "3.9.6", "3.9.9"),
                PackageUpdate("npm", "typescript-eslint", "8.66.0", "8.71.1"),
                PackageUpdate("npm", "vite", "8.2.0", "8.3.4"),
            ),
            update.updates,
        )
    }

    @Test
    fun `without an update column, a digest shows as commit hashes and is no version change either`() {
        val body =
            """
            This PR contains the following updates:

            | Package | Change |
            |---|---|
            | [services](https://redirect.github.com/p3k/json3k) | `ab0909d` → `fddaaa3` |
            | tools | `ab0909dc489d38b5d23bc1d2380e749a1f580ca2` → `fddaaa3cad6b9a782ebe732d41810043837cf394` |
            | [org.example:lib](https://example.org) | `1.0` → `abc1234` |
            """.trimIndent()

        assertEquals(listOf(PackageUpdate(null, "org.example:lib", "1.0", "abc1234")), source.read(pr(body = body), NoContext).updates)
    }

    @Test
    fun `release notes run to the end of a body without Renovate's footer`() {
        val body = "This PR contains the following updates:\n\n### Release Notes\n\n<details>\nnotes\n</details>\n\n---\n"

        assertEquals("<details>\nnotes\n</details>", source.read(pr(body = body), NoContext).releaseNotes)
    }

    @Test
    fun `a lock file maintenance PR has no version changes`() {
        assertEquals(emptyList<PackageUpdate>(), source.read(pr(body = captured("lock-file-maintenance.md")), NoContext).updates)
    }

    @Test
    fun `a group with a version change and a digest keeps only the version change`() {
        val body =
            """
            This PR contains the following updates:

            | Package | Type | Update | Change |
            |---|---|---|---|
            | [com.google.guava:guava](https://github.com/google/guava) | compile | major | `32.1.3-jre` -> `33.0.0-jre` |
            | eclipse-temurin | final | digest | `2a6d4b1` -> `9c0a3f7` |
            """.trimIndent()

        assertEquals(
            listOf(PackageUpdate(null, "com.google.guava:guava", "32.1.3-jre", "33.0.0-jre")),
            source.read(pr(body = body), NoContext).updates,
        )
    }

    @Test
    fun `without badges the ecosystem is unknown, and the older ASCII arrow is read too`() {
        val body =
            """
            This PR contains the following updates:

            | Package | Change |
            |---|---|
            | org.apache.commons:commons-lang3 | `3.12.0` -> `3.14.0` |
            """.trimIndent()

        assertEquals(
            listOf(PackageUpdate(null, "org.apache.commons:commons-lang3", "3.12.0", "3.14.0")),
            source.read(pr(body = body), NoContext).updates,
        )
    }

    @Test
    fun `rows of a datasource without an ecosystem are left out`() {
        val body =
            """
            This PR contains the following updates:

            | Package | Change | Age | Confidence |
            |---|---|---|---|
            | [actions/checkout](https://github.com/actions/checkout) | `v4` → `v5` | ![age](https://developer.mend.io/api/mc/badges/age/github-tags/actions%2fcheckout/v5?slim=true) | |
            | [org.example:lib](https://example.org) | `1.0` → `2.0` | ![age](https://developer.mend.io/api/mc/badges/age/maven/org.example:lib/2.0?slim=true) | |
            """.trimIndent()

        assertEquals(listOf(PackageUpdate("maven", "org.example:lib", "1.0", "2.0")), source.read(pr(body = body), NoContext).updates)
    }

    @Test
    fun `a body without the update table has no version changes`() {
        assertEquals(
            emptyList<PackageUpdate>(),
            source.read(pr(body = "Bump things.\n\n| a | b |\n|---|---|\n| x | `1` → `2` |"), NoContext).updates,
        )
        assertEquals(
            emptyList<PackageUpdate>(),
            source.read(pr(body = "This PR contains the following updates:\n\nnothing"), NoContext).updates,
        )
    }

    @Test
    fun `a row with a malformed change cell is skipped`() {
        val body =
            """
            This PR contains the following updates:

            | Package | Change |
            |---|---|
            | org.example:lib | 1.0 to 2.0 |
            | org.example:other |
            """.trimIndent()

        assertEquals(emptyList<PackageUpdate>(), source.read(pr(body = body), NoContext).updates)
    }

    private fun captured(name: String): String {
        val resource = assertNotNull(javaClass.getResource("/renovate/$name"), name)
        return resource.readText()
    }

    private fun pr(
        branch: String = "renovate/spring-boot",
        body: String = "",
    ) = PullRequest(7, "renovate[bot]", branch, "head", "acme/app", "acme/app", "base", body)
}
