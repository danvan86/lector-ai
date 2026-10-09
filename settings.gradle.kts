plugins {
    // Downloads the JDK 25 toolchain when it is missing.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "lector-ai"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("core", "cli")
