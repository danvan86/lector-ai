// The core knows no language and no bot. It must not depend on any other module.
plugins {
    id("lector.kotlin-conventions")
}

dependencies {
    implementation(libs.slf4j.api)
}
