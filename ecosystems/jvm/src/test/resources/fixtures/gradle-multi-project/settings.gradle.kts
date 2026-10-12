rootProject.name = "gradle-multi-project"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("lib", "app")
