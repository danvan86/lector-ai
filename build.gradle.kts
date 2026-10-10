plugins {
    // `base` gives the root project a `check` task, so `./gradlew build` runs the root ktlint checks too.
    base
    // Lints the root build scripts and buildSrc. The modules get ktlint from the convention plugin.
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    version = libs.versions.ktlint.asProvider()
    kotlinScriptAdditionalPaths {
        include(fileTree("buildSrc") { include("**/*.kts").exclude("build/**") })
    }
}
