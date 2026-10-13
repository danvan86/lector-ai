// The benchmark (M6): runs the tool's own code on a corpus of real dependency updates and reports the numbers. It uses
// `core` and the adapters directly, and is not part of the CLI distribution or the container image.
plugins {
    id("lector.kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":recipes"))
    implementation(project(":llm"))
    implementation(project(":ecosystems:jvm"))
    implementation(project(":ecosystems:typescript"))
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)
}

// Clikt's terminal detection loads JNA. Without this flag, JDK 25 warns about it on every run (as in `cli`).
val enableNativeAccess = "--enable-native-access=ALL-UNNAMED"

// `./gradlew :benchmark:run --args="<command> …"`, from the repository root. The workflow runs it; `./gradlew build` never does.
tasks.register<JavaExec>("run") {
    description = "Runs a benchmark command: matrix, run, merge, or report."
    group = "benchmark"
    mainClass = "gmbh.vanella.lector.benchmark.MainKt"
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs(enableNativeAccess)
    workingDir = rootDir
}

// `./gradlew :benchmark:report -Presults=benchmark/results/<date>`: report.md from results.json and, if it is there,
// review.json in that directory.
tasks.register<JavaExec>("report") {
    description = "Generates report.md from results.json and review.json in the directory -Presults=<dir>."
    group = "benchmark"
    mainClass = "gmbh.vanella.lector.benchmark.MainKt"
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs(enableNativeAccess)
    workingDir = rootDir
    val results = providers.gradleProperty("results")
    argumentProviders.add(CommandLineArgumentProvider { listOf("report", results.get()) })
}
