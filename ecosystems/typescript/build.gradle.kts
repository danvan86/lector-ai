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

// Tests tagged `integration` run Node, the package managers, tsc, Jest, and Vitest on the fixture repositories. They
// need Node 22 or newer and network access, so they are opt-in and not part of `check`. Corepack and jest-junit come
// from docker/lector/node, as in the tool image.
tasks.test {
    useJUnitPlatform { excludeTags("integration") }
}

val nodeToolchain = layout.buildDirectory.dir("node-toolchain")
val copyNodeToolchain =
    tasks.register<Copy>("copyNodeToolchain") {
        description = "Copies the tool image's Node toolchain manifest (docker/lector/node)."
        from(rootProject.layout.projectDirectory.dir("docker/lector/node"))
        into(nodeToolchain)
    }
val installNodeToolchain =
    tasks.register<Exec>("installNodeToolchain") {
        description = "Installs Corepack and jest-junit from docker/lector/node, as the tool image has them."
        dependsOn(copyNodeToolchain)
        inputs.files(copyNodeToolchain)
        outputs.dir(nodeToolchain.map { it.dir("node_modules") })
        outputs.dir(nodeToolchain.map { it.dir("bin") })
        workingDir(nodeToolchain)
        commandLine(
            "sh",
            "-c",
            "npm ci --omit=dev --ignore-scripts && mkdir -p bin && node_modules/.bin/corepack enable --install-directory bin pnpm yarn",
        )
    }

tasks.register<Test>("integrationTest") {
    description = "Runs the TypeScript adapter with real Node toolchains on the fixture repositories. Needs Node and network access."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    dependsOn(installNodeToolchain)
    val toolchain = nodeToolchain.get().asFile
    environment("PATH", toolchain.resolve("bin").path + File.pathSeparator + System.getenv("PATH"))
    environment("LECTOR_JEST_JUNIT", toolchain.resolve("node_modules/jest-junit").path)
    environment("COREPACK_ENABLE_DOWNLOAD_PROMPT", "0")
}

// Kover would make `check` depend on every test task. The integration tests stay opt-in.
kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.add("integrationTest")
        }
    }
}
