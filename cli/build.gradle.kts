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

// Clikt's terminal detection loads JNA. Without this flag, JDK 25 warns about it on every run.
val enableNativeAccess = "--enable-native-access=ALL-UNNAMED"

application {
    applicationName = "lector"
    mainClass = "gmbh.vanella.lector.cli.MainKt"
    applicationDefaultJvmArgs = listOf(enableNativeAccess)
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

tasks.withType<Test>().configureEach {
    jvmArgs(enableNativeAccess)
    systemProperty("lector.version", project.version.toString())
}

// Unit tests run in-process. Tests tagged `e2e` run the installed distribution, the same way the pipeline does.
tasks.test {
    useJUnitPlatform { excludeTags("e2e") }
}

val endToEndTest =
    tasks.register<Test>("endToEndTest") {
        description = "Runs the installed lector start script."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs =
            sourceSets.test
                .get()
                .output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { includeTags("e2e") }
        shouldRunAfter(tasks.test)
        dependsOn(tasks.installDist)
        systemProperty(
            "lector.bin",
            tasks.installDist
                .get()
                .destinationDir
                .resolve("bin/${application.applicationName}")
                .path,
        )
    }

tasks.check {
    dependsOn(endToEndTest)
}
