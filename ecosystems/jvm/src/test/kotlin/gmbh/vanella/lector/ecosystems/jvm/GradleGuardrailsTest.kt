package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DEFAULT_MAX_CHANGED_LINES
import gmbh.vanella.lector.core.FileChange
import gmbh.vanella.lector.core.Guardrails
import gmbh.vanella.lector.core.Violation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Gradle adapter's part of the scope and build-file guardrails. */
class GradleGuardrailsTest {
    private val adapter = GradleAdapter { _, _, _, _ -> error("no Gradle here") }

    @Test
    fun `build scripts, the version catalog, properties, and build logic are build files`() {
        for (file in listOf(
            "build.gradle",
            "settings.gradle",
            "build.gradle.kts",
            "settings.gradle.kts",
            "app/build.gradle.kts",
            "gradle/libs.versions.toml",
            "gradle.properties",
            "app/gradle.properties",
            "gradle/wrapper/gradle-wrapper.properties",
            "gradlew",
            "gradlew.bat",
            "buildSrc/src/main/kotlin/java-conventions.gradle.kts",
            "buildSrc/src/main/kotlin/Versions.kt",
            "build-logic/src/main/kotlin/Conventions.kt",
        )) {
            assertTrue(adapter.isBuildFile(file), file)
        }
    }

    @Test
    fun `sources and other files are not`() {
        for (file in listOf("src/main/java/com/example/App.java", "app/src/test/kotlin/AppTest.kt", "pom.xml", "README.md")) {
            assertFalse(adapter.isBuildFile(file), file)
        }
    }

    @Test
    fun `sources, tests, and build scripts are editable`() {
        for (file in listOf(
            "src/main/java/com/example/App.java",
            "app/src/test/kotlin/com/example/AppTest.kt",
            "build.gradle",
            "app/build.gradle.kts",
            "gradle/libs.versions.toml",
            "gradle.properties",
            "buildSrc/src/main/kotlin/Versions.kt",
        )) {
            assertTrue(adapter.isEditable(file), file)
        }
    }

    @Test
    fun `the wrapper, build output, Gradle's caches, generated code, and Groovy sources are not`() {
        for (file in listOf(
            "gradle/wrapper/gradle-wrapper.properties",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradlew",
            "gradlew.bat",
            "build/classes/java/main/App.class",
            "app/build/generated/sources/Api.java",
            ".gradle/9.1.0/checksums/checksums.lock",
            "buildSrc/.gradle/file-system.probe",
            "src/main/generated/com/example/Api.java",
            "src/test/groovy/com/example/AppSpec.groovy",
            "src/main/groovy/com/example/Helper.groovy",
        )) {
            assertFalse(adapter.isEditable(file), file)
        }
    }

    @Test
    fun `disabling a task, onlyIf, and ignoreFailures disable tests`() {
        val cases =
            mapOf(
                "    enabled = false" to "`enabled = false` disables a task, so it can disable tests",
                "    enabled false" to "`enabled false` disables a task, so it can disable tests",
                "    isEnabled = false" to "`isEnabled = false` disables a task, so it can disable tests",
                "tasks.test.get().setEnabled(false)" to "`setEnabled(false)` disables a task, so it can disable tests",
                "    onlyIf { false }" to "`onlyIf` makes a task conditional, so it can skip tests",
                "    setOnlyIf { false }" to "`setOnlyIf` makes a task conditional, so it can skip tests",
                "    ignoreFailures = true" to "`ignoreFailures` ignores test failures",
            )
        for ((line, reason) in cases) {
            assertEquals(reason, adapter.testDisablingConfig("build.gradle.kts", "tasks.test {\n$line\n}\n", 2), line)
        }
    }

    @Test
    fun `test filters and include or exclude patterns leave tests out`() {
        val cases =
            mapOf(
                "    filter { excludeTestsMatching(\"*Slow*\") }" to "`excludeTestsMatching` leaves tests out",
                "    useJUnitPlatform { excludeTags(\"slow\") }" to "`excludeTags` leaves tests out",
                "    useJUnitPlatform { excludeEngines(\"junit-vintage\") }" to "`excludeEngines` leaves tests out",
                "    filter { includeTestsMatching(\"*Fast*\") }" to "`includeTestsMatching` leaves tests out",
                "    useJUnitPlatform { includeTags(\"fast\") }" to "`includeTags` leaves tests out",
                "    filter { setExcludePatterns(['*Slow*']) }" to "`setExcludePatterns` leaves tests out",
                "    filter { includePatterns = ['*Fast*'] }" to "`includePatterns` leaves tests out",
                "    useJUnit { excludeCategories 'com.example.Slow' }" to "`excludeCategories` leaves tests out",
                "    useTestNG { excludeGroups 'slow' }" to "`excludeGroups` leaves tests out",
                "    exclude(\"**/SlowTest.class\")" to "`exclude` leaves tests or sources out",
                "    exclude '**/SlowTest.class'" to "`exclude` leaves tests or sources out",
                "    exclude { it.name.contains('Slow') }" to "`exclude` leaves tests or sources out",
                "    exclude(listOf(\"**/SlowTest*\"))" to "`exclude` leaves tests or sources out",
                "    excludes = ['**/SlowTest*']" to "`excludes` leaves tests or sources out",
                "    setExcludes(['**/SlowTest*'])" to "`setExcludes` leaves tests or sources out",
                "    include '**/FastTest*'" to "`include` leaves tests or sources out",
            )
        for ((line, reason) in cases) {
            assertEquals(reason, adapter.testDisablingConfig("build.gradle", "test {\n$line\n}\n", 2), line)
        }
    }

    @Test
    fun `dependency excludes and ordinary build changes are allowed`() {
        for (line in listOf(
            "    implementation(\"org.example:lib:2.0\") { exclude(group = \"commons-logging\") }",
            "    implementation('org.example:lib:2.0') { exclude group: 'commons-logging', module: 'commons-logging' }",
            "    configurations.all { exclude(module = \"commons-logging\") }",
            "    mavenCentral { content { excludeGroup(\"com.example\") } }",
            "    id(\"org.springframework.boot\") version \"3.4.1\"",
            "    useJUnitPlatform()",
            "    maxParallelForks = 2",
        )) {
            assertNull(adapter.testDisablingConfig("build.gradle.kts", "dependencies {\n$line\n}\n", 2), line)
        }
    }

    @Test
    fun `a settings file includes projects and builds, not test patterns`() {
        for (line in listOf("include(\":lib\", \":app\")", "include ':lib'", "includeBuild(\"build-logic\")")) {
            assertNull(adapter.testDisablingConfig("settings.gradle.kts", "$line\n", 1), line)
        }
    }

    @Test
    fun `the core's blocklist rejects a test filter added to a build script`() {
        val before = "tasks.test {\n    useJUnitPlatform()\n}\n"
        val after = "tasks.test {\n    useJUnitPlatform { excludeTags(\"slow\") }\n}\n"
        val guardrails = Guardrails(adapter, DEFAULT_MAX_CHANGED_LINES)

        val violations = guardrails.check(listOf(FileChange("app/build.gradle.kts", before, after, adapter.languageSupport)), emptyList())

        assertEquals(listOf(Violation("build-file", "app/build.gradle.kts", 2, "`excludeTags` leaves tests out")), violations)
    }
}
