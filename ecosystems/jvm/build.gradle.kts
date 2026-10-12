// The JVM ecosystem adapters, Maven and Gradle. They drive the build tool as a process and parse its output, and read
// Java and Kotlin sources with tree-sitter for the guardrails.
plugins {
    id("lector.kotlin-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.slf4j.api)
    implementation(libs.maven.artifact)
    // The model the Gradle adapter's init script writes as JSON.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tree.sitter)
    implementation(libs.tree.sitter.java)
    implementation(libs.tree.sitter.kotlin)
}

// tree-sitter loads its native libraries through JNI. Without this flag, JDK 25 warns about it.
tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// Tests tagged `integration` run real Gradle on the fixture repos and need network access, so they are opt-in and not
// part of `check`.
tasks.test {
    useJUnitPlatform { excludeTags("integration") }
}

tasks.register<Test>("integrationTest") {
    description = "Runs the Gradle adapter with real Gradle on the fixture repositories. Needs network access."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    gradleFixtures()
}

// Kover would make `check` depend on every test task. The integration tests stay opt-in.
kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.add("integrationTest")
        }
    }
}

// The adapter calls these Maven plugins by coordinates. Their versions come from the version catalog, through this
// generated resource.
val pluginResources = layout.buildDirectory.dir("generated/resources/maven-plugins")
val generateMavenPluginsResource =
    tasks.register<WriteProperties>("generateMavenPluginsResource") {
        destinationFile = pluginResources.map { it.file("gmbh/vanella/lector/ecosystems/jvm/maven-plugins.properties") }
        property(
            "rewrite",
            libs.rewrite.maven.plugin
                .get()
                .toString(),
        )
        property(
            "help",
            libs.maven.help.plugin
                .get()
                .toString(),
        )
    }
sourceSets.main {
    resources.srcDir(files(pluginResources).builtBy(generateMavenPluginsResource))
}
