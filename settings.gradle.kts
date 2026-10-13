plugins {
    // Downloads the JDK 25 toolchain when it is missing.
    // The only version outside the catalog: the settings `plugins {}` block can't read the catalog.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "lector-ai"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // The OpenRewrite Gradle plugin is published only to the Gradle Plugin Portal. Nothing else comes from there.
        exclusiveContent {
            forRepository { gradlePluginPortal() }
            filter { includeModule("org.openrewrite", "plugin") }
        }
    }
}

include("core", "recipes", "llm", "sources", "git", "cli", "ecosystems:jvm", "benchmark")
