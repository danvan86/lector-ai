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

kover {
    reports {
        verify {
            rule {
                minBound(80, CoverageUnit.LINE)
                minBound(70, CoverageUnit.BRANCH)
            }
        }
    }
}
