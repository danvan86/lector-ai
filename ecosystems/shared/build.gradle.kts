// What the ecosystem adapters share, outside the core: the JUnit XML reports most test runners write, the facts that
// tree-sitter queries report about source files, and a safe XML parser. It knows no ecosystem.
plugins {
    id("lector.kotlin-conventions")
}

dependencies {
    implementation(project(":core"))
    // The adapters pass their grammars (TSLanguage) in.
    api(libs.tree.sitter)
    testImplementation(libs.tree.sitter.java)
}

// tree-sitter loads its native libraries through JNI. Without this flag, JDK 25 warns about it.
tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
