// Update-source adapters: turn a bot's PR into version changes and release notes. Depends on `core` only.
plugins {
    id("lector.kotlin-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.slf4j.api)
}
