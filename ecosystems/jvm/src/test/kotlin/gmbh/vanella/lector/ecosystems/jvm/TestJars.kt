package gmbh.vanella.lector.ecosystems.jvm

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import javax.tools.ToolProvider
import kotlin.io.path.createParentDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.assertEquals

/** Compiles Java [sources] (class name → source) and packs the classes into a JAR at [jar]. */
internal fun buildJar(
    jar: Path,
    sources: Map<String, String>,
) {
    val src = Files.createTempDirectory("lector-jar-src")
    val classes = Files.createTempDirectory("lector-jar-classes")
    val files =
        sources.map { (name, source) ->
            src.resolve(name.replace('.', '/') + ".java").createParentDirectories().apply { writeText(source) }
        }
    val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "The tests need a JDK" }
    val exit = compiler.run(null, null, null, "-d", classes.toString(), *files.map { it.toString() }.toTypedArray())
    assertEquals(0, exit, "the test sources compile")
    JarOutputStream(jar.createParentDirectories().outputStream(), Manifest()).use { out ->
        classes.walk().filter { it.isRegularFile() }.sorted().forEach { file ->
            out.putNextEntry(ZipEntry(classes.relativize(file).invariantSeparatorsPathString))
            out.write(file.readBytes())
            out.closeEntry()
        }
    }
}
