package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.Severity
import gmbh.vanella.lector.core.UpdateType
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains

/** Which classes are diffed once the errors refer to the update: every class the erroring file imports, too. */
class ApiChangesSelectionTest {
    @TempDir
    lateinit var repo: Path

    @TempDir
    lateinit var localRepository: Path

    @Test
    fun `an imported class whose constructor changed is diffed, though javac names only the new parameter's type`() {
        val change = DependencyChange("maven", "org.yaml:snakeyaml", "1.33", "2.0", UpdateType.MAJOR)
        val options = "package org.yaml.snakeyaml; public class LoaderOptions {}"
        buildJar(
            localRepository.resolve("org/yaml/snakeyaml/1.33/snakeyaml-1.33.jar"),
            mapOf(
                "org.yaml.snakeyaml.LoaderOptions" to options,
                "org.yaml.snakeyaml.constructor.Constructor" to
                    "package org.yaml.snakeyaml.constructor; public class Constructor { public Constructor(Class<?> type) {} " +
                    "public Constructor(org.yaml.snakeyaml.LoaderOptions options) {} }",
            ),
        )
        buildJar(
            localRepository.resolve("org/yaml/snakeyaml/2.0/snakeyaml-2.0.jar"),
            mapOf(
                "org.yaml.snakeyaml.LoaderOptions" to options,
                "org.yaml.snakeyaml.constructor.Constructor" to
                    "package org.yaml.snakeyaml.constructor; public class Constructor { " +
                    "public Constructor(Class<?> type, org.yaml.snakeyaml.LoaderOptions options) {} " +
                    "public Constructor(org.yaml.snakeyaml.LoaderOptions options) {} }",
            ),
        )
        val file = "src/main/java/com/example/ConfigLoader.java"
        val source = "package com.example;\n\nimport org.yaml.snakeyaml.constructor.Constructor;\n"
        repo.resolve(file).createParentDirectories().writeText(source)
        val error =
            Diagnostic(
                file,
                14,
                50,
                null,
                "incompatible types: java.lang.Class<com.example.Config> cannot be converted to org.yaml.snakeyaml.LoaderOptions",
                Severity.ERROR,
                "javac",
            )

        val text = MavenAdapter(localRepository = localRepository).apiChanges(repo, change, listOf(error))

        assertContains(
            text,
            "- org.yaml.snakeyaml.constructor.Constructor: (java.lang.Class), (org.yaml.snakeyaml.LoaderOptions) → " +
                "(java.lang.Class, org.yaml.snakeyaml.LoaderOptions), (org.yaml.snakeyaml.LoaderOptions)",
        )
    }
}
