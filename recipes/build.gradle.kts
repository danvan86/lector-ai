// The recipe index: which OpenRewrite recipe fits a dependency change. Depends on `core` only.
//
// The index is generated at build time from the recipes of `rewrite-recipe-bom` and ships as a resource. OpenRewrite is
// only on the classpath of the `generator` source set, which runs during the build and is not part of the distribution.

import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("lector.kotlin-conventions")
}

// The generator uses the index format and builder from `main`. Only the compiled classes: `main`'s resources are what
// the generator produces, so depending on all of `main` would be a cycle.
val mainClasses = files(tasks.compileKotlin.flatMap { it.destinationDirectory })
val generator =
    sourceSets.create("generator") {
        compileClasspath += mainClasses + configurations.compileClasspath.get()
        runtimeClasspath += mainClasses + configurations.runtimeClasspath.get()
    }

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)

    "generatorImplementation"(platform(libs.rewrite.recipe.bom))
    "generatorImplementation"(libs.rewrite.core)
    // Some recipes log through SLF4J while they are loaded.
    "generatorRuntimeOnly"(libs.slf4j.simple)
}

// Every recipe module the BOM names. Read from the BOM itself, so a new module is indexed without touching this file.
val recipeBom =
    libs.rewrite.recipe.bom
        .get()
val recipeBomPom = configurations.detachedConfiguration(dependencies.create("$recipeBom@pom"))
configurations.named("generatorImplementation") {
    withDependencies {
        recipeModules(recipeBomPom.singleFile).forEach { add(project.dependencies.create(it)) }
    }
}

fun recipeModules(bom: File): List<String> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bom)
    val dependencies = document.getElementsByTagName("dependency")
    return (0 until dependencies.length)
        .map { dependencies.item(it) as org.w3c.dom.Element }
        .map { it.getElementsByTagName("groupId").item(0).textContent to it.getElementsByTagName("artifactId").item(0).textContent }
        .filter { (group, _) -> group == "org.openrewrite.recipe" }
        .map { (group, artifact) -> "$group:$artifact" }
}

// The generator runs in the build, so its tests run with OpenRewrite on the classpath. The `main` tests don't need it.
sourceSets.test {
    compileClasspath += generator.output
    runtimeClasspath += generator.output
}
configurations.testImplementation { extendsFrom(configurations["generatorImplementation"]) }
configurations.testRuntimeOnly { extendsFrom(configurations["generatorRuntimeOnly"]) }

val indexDir = layout.buildDirectory.dir("generated/recipe-index")
val generateRecipeIndex =
    tasks.register<JavaExec>("generateRecipeIndex") {
        description = "Generates the recipe index and its report from the recipes of rewrite-recipe-bom."
        classpath = generator.runtimeClasspath
        mainClass = "gmbh.vanella.lector.recipes.generator.GenerateRecipeIndexKt"
        outputs.dir(indexDir)
        args(
            indexDir
                .get()
                .dir("gmbh/vanella/lector/recipes")
                .asFile.path,
            recipeBom.toString(),
        )
        // The coordinates of every JAR, as Gradle resolved them: `<path>=<group>:<name>:<version>`.
        val jars = configurations.named("generatorRuntimeClasspath").flatMap { it.incoming.artifacts.resolvedArtifacts }
        argumentProviders.add {
            jars.get().mapNotNull { jar ->
                (jar.id.componentIdentifier as? ModuleComponentIdentifier)?.let { "${jar.file.path}=${it.displayName}" }
            }
        }
    }
sourceSets.main {
    resources.srcDir(files(indexDir).builtBy(generateRecipeIndex))
}

// The convention plugin wires detekt for `main` and `test`. The generator gets the same check.
tasks.check {
    dependsOn("detektGenerator")
}
