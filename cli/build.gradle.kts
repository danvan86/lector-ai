plugins {
    id("lector.kotlin-conventions")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":recipes"))
    implementation(project(":llm"))
    implementation(project(":ecosystems:jvm"))
    implementation(project(":ecosystems:typescript"))
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

// The OpenRewrite Gradle plugin and what it needs at run time (the Kotlin standard library), as plain JARs in the
// distribution's `rewrite-gradle-plugin/` directory. The Gradle adapter's recipe init script puts them on its classpath,
// so no Gradle build has to fetch the plugin (M7 Q7). The tool image points LECTOR_REWRITE_GRADLE_PLUGIN at them.
val rewriteGradlePlugin =
    configurations.create("rewriteGradlePlugin") {
        isCanBeConsumed = false
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
dependencies {
    rewriteGradlePlugin(libs.rewrite.gradle.plugin)
}
val rewriteGradlePluginDir = layout.buildDirectory.dir("rewrite-gradle-plugin")
val syncRewriteGradlePlugin =
    tasks.register<Sync>("syncRewriteGradlePlugin") {
        description = "Copies the OpenRewrite Gradle plugin and its run-time JARs to one directory."
        from(rewriteGradlePlugin)
        into(rewriteGradlePluginDir)
    }
distributions.main {
    contents {
        from(syncRewriteGradlePlugin) { into("rewrite-gradle-plugin") }
    }
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
    description = "Runs the commands with real Maven and Gradle on fixture repositories. Needs Maven and network access."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    // Recipes in Gradle builds need the plugin, as in the tool image.
    dependsOn(syncRewriteGradlePlugin)
    environment("LECTOR_REWRITE_GRADLE_PLUGIN", rewriteGradlePluginDir.get().asFile.path)
    gradleFixtures()
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
                    .single { it.startsWith("FROM ollama/ollama:") }
                    .removePrefix("FROM ")
                    .substringBefore(' ')
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
