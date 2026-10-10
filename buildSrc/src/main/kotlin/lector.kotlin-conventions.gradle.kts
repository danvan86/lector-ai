// Shared build conventions for every module: Kotlin, JDK toolchain, tests, and the quality gates.
// The gates are part of `check`, so `./gradlew build` fails on any finding.

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
    id("org.jetbrains.kotlinx.kover")
}

val libs = versionCatalogs.named("libs")

kotlin {
    jvmToolchain(25)
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    version = libs.findVersion("ktlint").get().requiredVersion
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// The type-resolving tasks, because some rules in our config only work with type resolution.
tasks.check {
    dependsOn("detektMain", "detektTest")
}

// The plain task runs without type resolution and only repeats the findings of the tasks above.
tasks.named("detekt") {
    enabled = false
}

// Every line and branch must be covered. The one exclusion is the `main` entry point: it exits the process,
// so it can't run in-process. The end-to-end tests run it.
kover {
    reports {
        filters {
            excludes {
                classes("*.MainKt")
            }
        }
        verify {
            rule {
                minBound(100, CoverageUnit.LINE)
                minBound(100, CoverageUnit.BRANCH)
            }
        }
    }
}
