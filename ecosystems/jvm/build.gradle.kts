// The JVM ecosystem adapter: Maven for now. It drives Maven as a process and parses its output.
plugins {
    id("lector.kotlin-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.slf4j.api)
}
