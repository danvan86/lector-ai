// The JVM ecosystem adapter: Maven for now. It drives Maven as a process and parses its output.
plugins {
    id("lector.kotlin-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.slf4j.api)
    implementation(libs.maven.artifact)
}

// The adapter calls these Maven plugins by coordinates. Their versions come from the version catalog, through this
// generated resource.
val pluginResources = layout.buildDirectory.dir("generated/resources/maven-plugins")
val generateMavenPluginsResource =
    tasks.register<WriteProperties>("generateMavenPluginsResource") {
        destinationFile = pluginResources.map { it.file("gmbh/vanella/lector/ecosystems/jvm/maven-plugins.properties") }
        property(
            "rewrite",
            libs.rewrite.maven.plugin
                .get()
                .toString(),
        )
        property(
            "help",
            libs.maven.help.plugin
                .get()
                .toString(),
        )
    }
sourceSets.main {
    resources.srcDir(files(pluginResources).builtBy(generateMavenPluginsResource))
}
