// The LLM adapter: Ollama over HTTP, and the prompts. Depends on `core` only.
plugins {
    id("lector.kotlin-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
}
