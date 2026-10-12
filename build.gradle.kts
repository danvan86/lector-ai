plugins {
    // `base` gives the root project a `check` task, so `./gradlew build` runs the root ktlint checks too.
    base
    // Lints the root build scripts and buildSrc. The modules get ktlint from the convention plugin.
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    version = libs.versions.ktlint.asProvider()
    kotlinScriptAdditionalPaths {
        include(fileTree("buildSrc") { include("**/*.kts", "**/*.kt").exclude("build/**") })
    }
}

// The action runs the tool image of the same version. A release bumps both, or the action runs an older tool.
val checkActionImage =
    tasks.register("checkActionImage") {
        description = "Checks that action/action.yml runs the tool image of this version."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        val action = layout.projectDirectory.file("action/action.yml")
        val version = providers.gradleProperty("version")
        inputs.file(action)
        inputs.property("version", version)
        doLast {
            val image =
                action.asFile
                    .readLines()
                    .map { it.trim() }
                    .single { it.startsWith("image:") }
            check(image.endsWith("/lector:${version.get()}")) {
                "action/action.yml runs $image, but the tool's version is ${version.get()}. Update the image tag."
            }
        }
    }

tasks.check {
    dependsOn(checkActionImage)
}
