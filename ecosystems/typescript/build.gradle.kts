// The TypeScript ecosystem adapter: npm, pnpm, and Yarn (classic and Berry) repositories, with or without workspaces.
// It drives Node, the package managers, tsc, Jest, and Vitest as processes and parses their output, reads the
// lockfiles, and reads TypeScript and JavaScript sources and declaration files with tree-sitter.
plugins {
    id("lector.kotlin-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ecosystems:shared"))
    implementation(libs.slf4j.api)
    // package.json, package-lock.json, and tsconfig.json.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.snakeyaml.engine)
    implementation(libs.tree.sitter.typescript)
    implementation(libs.tree.sitter.tsx)
    implementation(libs.tree.sitter.javascript)
}

// tree-sitter loads its native libraries through JNI. Without this flag, JDK 25 warns about it.
tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
