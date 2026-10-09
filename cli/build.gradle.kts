plugins {
    id("lector.kotlin-conventions")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(libs.clikt)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)
}

application {
    applicationName = "lector"
    mainClass = "gmbh.vanella.lector.cli.MainKt"
    // Clikt's terminal detection loads JNA. Without this flag, JDK 25 warns about it on every run.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

// The Gradle `version` is the only source of the tool's version. The CLI reads it from this generated resource.
val versionResources = layout.buildDirectory.dir("generated/resources/version")
val generateVersionResource =
    tasks.register<WriteProperties>("generateVersionResource") {
        destinationFile = versionResources.map { it.file("gmbh/vanella/lector/cli/version.properties") }
        property("version", project.version.toString())
    }
sourceSets.main {
    resources.srcDir(files(versionResources).builtBy(generateVersionResource))
}

// The end-to-end tests run the installed distribution, the same way the pipeline does.
tasks.test {
    dependsOn(tasks.installDist)
    systemProperty("lector.version", project.version.toString())
    systemProperty(
        "lector.bin",
        layout.buildDirectory
            .file("install/lector/bin/lector")
            .get()
            .asFile.path,
    )
}
