plugins {
    id("lector.kotlin-conventions")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":recipes"))
    implementation(project(":llm"))
    implementation(project(":ecosystems:jvm"))
    implementation(project(":sources"))
    implementation(project(":git"))
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.ollama)
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
// Tests tagged `integration` run real Maven and need network access, so they are opt-in and not part of `check`.
// So is the test tagged `real-model`: it runs a real model in Ollama, in Docker.
tasks.test {
    useJUnitPlatform { excludeTags("e2e", "integration", "real-model") }
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

tasks.register<Test>("integrationTest") {
    description = "Runs verify with real Maven on a fixture repository. Needs Maven and network access."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
}

tasks.register<Test>("realModelTest") {
    description = "Runs heal with a real model in Ollama on a fixture repository. Needs Docker, Maven, and network access."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("real-model") }
    // The Ollama of the model image, so the test runs what the release ships.
    val ollamaImage =
        providers
            .fileContents(rootProject.layout.projectDirectory.file("docker/ollama/Dockerfile"))
            .asText
            .map { dockerfile ->
                dockerfile
                    .lines()
                    .single { it.startsWith("FROM ") }
                    .removePrefix("FROM ")
                    .trim()
            }
    systemProperty("lector.ollamaImage", ollamaImage.get())
    // Cluster keys and grounding read the English compiler messages, as in the container image and on CI runners.
    environment("MAVEN_OPTS", "-Duser.language=en -Duser.country=US")
    // `-Plector.model=<name>` tests another model. Without it, the test uses the CLI's default.
    providers
        .gradleProperty("lector.model")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { systemProperty("lector.model", it) }
}

// Kover would make `check` depend on every test task. The integration and real-model tests stay opt-in.
kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.addAll("integrationTest", "realModelTest")
        }
    }
}
