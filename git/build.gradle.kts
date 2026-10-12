// Git host adapters: the PR from the event, comments, labels, fetch, and push. Depends on `core` only.
plugins {
    id("lector.kotlin-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
}
