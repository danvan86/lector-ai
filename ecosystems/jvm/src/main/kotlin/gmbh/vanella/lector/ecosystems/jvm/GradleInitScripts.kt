package gmbh.vanella.lector.ecosystems.jvm

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.writeText

private const val LECTOR = "lector.init.gradle"
private const val REWRITE = "lector-rewrite.init.gradle"

/**
 * The Gradle adapter's init scripts, written from the resources to a temporary directory on first use. [close] removes
 * them; the next use writes them again.
 */
internal class GradleInitScripts : AutoCloseable {
    private var directory: Path? = null

    /** The script with lector-ai's tasks, on every run. */
    fun lector() = script(LECTOR)

    /** The recipe script, loading the OpenRewrite plugin's JARs from the directory [plugin]. */
    fun rewrite(plugin: Path): Path {
        val path =
            plugin
                .toAbsolutePath()
                .toString()
                .replace("\\", "\\\\")
                .replace("'", "\\'")
        return script(REWRITE) { it.replace("@PLUGIN_DIRECTORY@", path) }
    }

    @OptIn(ExperimentalPathApi::class)
    override fun close() {
        directory?.deleteRecursively()
        directory = null
    }

    private fun script(
        name: String,
        fill: (String) -> String = { it },
    ): Path {
        val file = (directory ?: Files.createTempDirectory("lector-gradle").also { directory = it }).resolve(name)
        if (!file.exists()) file.writeText(fill(resourceText(name)))
        return file
    }
}

/** The text of the resource [name] next to [GradleInitScripts]. */
internal fun resourceText(name: String): String =
    checkNotNull(GradleInitScripts::class.java.getResource(name)) { "$name is missing from the classpath" }.readText()
