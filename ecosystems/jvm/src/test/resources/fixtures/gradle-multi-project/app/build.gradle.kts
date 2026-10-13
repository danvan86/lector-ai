plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":lib"))
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter("5.11.4")
            dependencies {
                implementation(project())
            }
        }
    }
}

// A linter bound to `check`. lector-ai's verify never runs it.
val lint =
    tasks.register("lint") {
        doLast { throw GradleException("lint: App.java is not formatted") }
    }

tasks.check {
    dependsOn(lint, testing.suites.named("integrationTest"))
}

application {
    mainClass = "com.example.app.App"
}
