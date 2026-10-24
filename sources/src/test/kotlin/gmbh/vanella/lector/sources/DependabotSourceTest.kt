package gmbh.vanella.lector.sources

import gmbh.vanella.lector.core.BranchPolicy
import gmbh.vanella.lector.core.CommitIdentity
import gmbh.vanella.lector.core.PackageUpdate
import gmbh.vanella.lector.core.PullRequest
import gmbh.vanella.lector.core.ToolError
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reads Dependabot PRs captured from public repositories (`src/test/resources/dependabot/`): the PR body, and the
 * `updated-dependencies-json` that `dependabot/fetch-metadata`'s own parser made of the PR's commit message.
 * `maven-group`: mailtoharutyunyan/Praxis#12, `gradle-plugin`: team-native/GYMUP#2, `gradle-group`:
 * ChristianHerget/trackglance#107, `npm`: manuelbasanta/GiftList#6.
 */
class DependabotSourceTest {
    private val logins = setOf("renovate[bot]", "dependabot[bot]")

    @Test
    fun `recognizes a PR by a bot login and the dependabot branch prefix`() {
        val source = DependabotSource(logins, metadata = null)

        assertTrue(source.recognizes(pr(branch = "dependabot/maven/maven-532e966310")))
        assertFalse(source.recognizes(pr(branch = "renovate/spring-boot")))
        assertFalse(source.recognizes(pr(branch = "feature/dependabot/x")))
        assertFalse(source.recognizes(pr(branch = "dependabot/maven/x").copy(author = "mallory")))
    }

    @Test
    fun `is called dependabot and fixes in a companion PR`() {
        val source = DependabotSource(logins, metadata = null)

        assertEquals("dependabot", source.name)
        assertEquals(BranchPolicy.COMPANION, source.branchPolicy)
        assertNull(source.setupHint(CommitIdentity("my-lector[bot]", "123+my-lector[bot]@users.noreply.github.com")))
    }

    @Test
    fun `every entry of a grouped update is a change, in the order of the metadata`() {
        val update = read("maven-group")

        assertEquals(
            listOf(
                PackageUpdate("maven", "commons-io:commons-io", "2.21.0", "2.22.0"),
                PackageUpdate("maven", "com.google.guava:guava", "33.4.8-jre", "33.7.1-jre"),
                PackageUpdate("maven", "com.squareup.okio:okio-jvm", "3.16.4", "3.18.2"),
                PackageUpdate("maven", "io.swagger.core.v3:swagger-annotations", "2.2.50", "2.2.55"),
            ),
            update.updates,
        )
    }

    @Test
    fun `a Gradle plugin maps to its marker coordinates in the maven ecosystem`() {
        val update = read("gradle-plugin")

        assertEquals(
            listOf(PackageUpdate("maven", "org.springframework.boot:org.springframework.boot.gradle.plugin", "4.1.0", "4.1.1")),
            update.updates,
        )
    }

    @Test
    fun `a Gradle group keeps its plugins and leaves out the Gradle wrapper, which is no package`() {
        val update = read("gradle-group", body = "")

        assertEquals(
            listOf(
                PackageUpdate(
                    "maven",
                    "org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin",
                    "2.4.20",
                    "2.4.21",
                ),
                PackageUpdate("maven", "com.diffplug.spotless:com.diffplug.spotless.gradle.plugin", "8.10.3", "8.10.4"),
            ),
            update.updates,
        )
    }

    @Test
    fun `npm_and_yarn, what fetch-metadata reports for npm, pnpm, and Yarn, is the npm ecosystem, and so is npm`() {
        assertEquals(listOf(PackageUpdate("npm", "axios", "1.2.0", "1.20.0")), read("npm", body = "").updates)
        val metadata = DependabotSource.parseMetadata(captured("npm.json").replace("npm_and_yarn", "npm"))

        assertEquals(listOf("npm"), DependabotSource(logins, metadata).read(pr(body = ""), NoContext).updates.map { it.ecosystem })
    }

    @Test
    fun `an ecosystem other than Maven's, Gradle's, and npm's produces no change`() {
        val metadata = DependabotSource.parseMetadata(captured("npm.json").replace("npm_and_yarn", "pip"))

        assertEquals(emptyList(), DependabotSource(logins, metadata).read(pr(body = ""), NoContext).updates)
    }

    @Test
    fun `the update type is not read from Dependabot`() {
        val metadata = captured("gradle-plugin.json").replace("version-update:semver-patch", "version-update:semver-major")

        val update = DependabotSource(logins, DependabotSource.parseMetadata(metadata)).read(pr(), NoContext)

        assertEquals(read("gradle-plugin").updates, update.updates)
    }

    @Test
    fun `an entry without a previous or a new version is left out`() {
        val metadata =
            """[{"dependencyName": "a:b", "packageEcosystem": "maven", "prevVersion": "", "newVersion": "2.0"},
               {"dependencyName": "c:d", "packageEcosystem": "maven", "prevVersion": "1.0", "newVersion": ""}]"""

        val update = DependabotSource(logins, DependabotSource.parseMetadata(metadata)).read(pr(), NoContext)

        assertEquals(emptyList(), update.updates)
    }

    @Test
    fun `the release notes and changelogs of the PR body are the release notes, with the update each belongs to`() {
        val notes = assertNotNull(read("maven-group").releaseNotes)

        assertContains(notes, "Updates `com.google.guava:guava` from 33.4.8-jre to 33.7.1-jre\n<p><em>Sourced from")
        assertContains(notes, "Guava 33.7.1 removes the <code>Multi-Release</code> line")
        assertContains(notes, "Updates `com.squareup.okio:okio-jvm` from 3.16.4 to 3.18.2\n<p><em>Sourced from")
        assertContains(notes, "Fix: Don't change behavior of base64-encoding.")
        assertFalse("See full diff in" in notes, "the commit lists are no release notes")
        assertFalse("@dependabot rebase" in notes)
        assertFalse("commons-io" in notes, "an update without notes has no section")
    }

    @Test
    fun `the release notes of a single update are headed by its bump line`() {
        val notes = assertNotNull(read("gradle-plugin").releaseNotes)

        assertTrue(
            notes.startsWith(
                "Bumps [org.springframework.boot](https://github.com/spring-projects/spring-boot) from 4.1.0 to 4.1.1.\n" +
                    "<p><em>Sourced from",
            ),
            notes.take(300),
        )
        assertContains(notes, "Spring Boot's Gradle plugin no longer automatically configures gRPC")
    }

    @Test
    fun `release notes without a line that names their update stand alone`() {
        val body = "<details>\n<summary>Changelog</summary>\n<p>Fixed it.</p>\n</details>"

        assertEquals("<p>Fixed it.</p>", read("gradle-plugin", body = body).releaseNotes)
    }

    @Test
    fun `a body without release notes has none`() {
        assertNull(
            read("gradle-plugin", body = "Bumps x from 1 to 2.\n\n<details>\n<summary>Commits</summary>\nabc\n</details>").releaseNotes,
        )
    }

    @Test
    fun `a Dependabot PR without the metadata is a tool error that names the setup step`() {
        val error = assertFailsWith<ToolError> { DependabotSource(logins, metadata = null).read(pr(), NoContext) }

        assertEquals(
            "The Dependabot PR comes without its metadata. Run dependabot/fetch-metadata in the workflow and pass its " +
                "updated-dependencies-json output to the action's dependabot-metadata input, as the setup guide shows (docs/setup.md, section 3).",
            error.message,
        )
    }

    @Test
    fun `metadata that isn't fetch-metadata's JSON is a tool error`() {
        listOf(
            "{" to "not valid JSON",
            "{}" to "not a JSON array",
            """[{"dependencyName": "a:b"}]""" to "an entry has no packageEcosystem",
            """[{"dependencyName": 1, "packageEcosystem": "maven", "prevVersion": "1", "newVersion": "2"}]""" to
                "dependencyName is not a string",
            """[{"dependencyName": "a:b", "packageEcosystem": ["maven"]}]""" to "packageEcosystem is not a string",
            "[1]" to "an entry is not a JSON object",
        ).forEach { (json, reason) ->
            val error = assertFailsWith<ToolError>(json) { DependabotSource.parseMetadata(json) }

            assertContains(error.message, "The Dependabot metadata is not fetch-metadata's updated-dependencies-json: ", message = json)
            assertContains(error.message, reason, message = json)
        }
    }

    private fun read(
        name: String,
        body: String = captured("$name.md"),
    ) = DependabotSource(logins, DependabotSource.parseMetadata(captured("$name.json"))).read(pr(body = body), NoContext)

    private fun captured(name: String): String {
        val resource = assertNotNull(javaClass.getResource("/dependabot/$name"), name)
        return resource.readText()
    }

    private fun pr(
        branch: String = "dependabot/maven/maven-532e966310",
        body: String = "",
    ) = PullRequest(7, "dependabot[bot]", branch, "head", "acme/app", "acme/app", "base", body)
}
