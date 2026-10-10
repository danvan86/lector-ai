// The recipe index: which OpenRewrite recipe fits a dependency change. Depends on `core` only.
plugins {
    id("lector.kotlin-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
}
