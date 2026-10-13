package gmbh.vanella.lector.ecosystems.typescript

import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

private const val ZIP = ".zip/"
private val VIRTUAL = Regex("""^(.*/__virtual__)/[^/]+/(\d+)/(.*)$""")

/**
 * The files of one installed package, by their path relative to the package's directory (`/` as separator): in
 * `node_modules`, or under Plug'n'Play inside the zip archive Yarn keeps it in (spec section 5). Null if there is no
 * such file.
 */
internal fun interface PackageFiles {
    fun read(path: String): String?

    companion object {
        /** The package at [location]: a directory, or a directory inside a zip archive (`…/cache/x.zip/node_modules/x/`). */
        fun at(location: String): PackageFiles {
            val real = real(location)
            if (ZIP !in real) {
                return PackageFiles { file ->
                    Path
                        .of(real)
                        .resolve(file)
                        .takeIf { it.isRegularFile() }
                        ?.readText()
                }
            }
            val zip = Path.of(real.substringBefore(ZIP) + ".zip")
            val prefix = real.substringAfter(ZIP).removeSuffix("/")
            return PackageFiles { file -> readZipEntry(zip, "$prefix/$file") }
        }

        /**
         * The real path of a package Yarn gives a virtual path, because it has peer dependencies:
         * `<dir>/.yarn/__virtual__/<hash>/<n>/<rest>` is `<rest>` [n] directories above `<dir>/.yarn`.
         */
        private fun real(location: String): String {
            val virtual = VIRTUAL.matchEntire(location) ?: return location
            val (folder, depth, rest) = virtual.destructured
            val base = (1..depth.toInt()).fold(Path.of(folder).parent) { dir, _ -> dir.parent }
            return base.resolve(rest).toString() + if (location.endsWith("/")) "/" else ""
        }

        private fun readZipEntry(
            zip: Path,
            entry: String,
        ): String? =
            try {
                ZipFile(zip.toFile()).use { archive ->
                    archive.getEntry(entry)?.let {
                        archive.getInputStream(it).use { input ->
                            input.readBytes().decodeToString()
                        }
                    }
                }
            } catch (_: IOException) {
                null
            }
    }
}

/** [path] (relative to a package, `/` as separator) without `.` and `..` segments, or null if it leaves the package. */
internal fun normalizePackagePath(path: String): String? {
    val parts = mutableListOf<String>()
    for (part in path.split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeLast()
            else -> parts += part
        }
    }
    return parts.joinToString("/")
}
