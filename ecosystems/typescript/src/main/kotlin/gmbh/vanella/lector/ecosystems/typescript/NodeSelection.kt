package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.ToolError
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

private val logger = LoggerFactory.getLogger(NodeSelection::class.java)

private val VERSION_FILES = listOf(".nvmrc", ".node-version")
private val VERSION = Regex("""v?(\d{1,9})(?:\.\d+){0,2}""")
private val HEADER = Regex("""#define NODE_(MAJOR|MINOR|PATCH)_VERSION (\d+)""")
private const val PARTS = 3

// The LTS lines by codename, as nvm names them (`lts/jod`).
private val CODENAMES =
    mapOf(
        "argon" to 4,
        "boron" to 6,
        "carbon" to 8,
        "dubnium" to 10,
        "erbium" to 12,
        "fermium" to 14,
        "gallium" to 16,
        "hydrogen" to 18,
        "iron" to 20,
        "jod" to 22,
        "krypton" to 24,
    )

/**
 * Picks the Node a project runs on from the [installed] ones (major → installation): the major its `.nvmrc` or
 * `.node-version` names (a version, a major, the newest LTS, or an LTS codename), else the highest installed Node that
 * `engines.node` allows, else the newest LTS (the highest even major). The image has one Node per major, so a version
 * file's minor and patch don't matter. Without installed Nodes, nothing is picked and the `PATH`'s `node` runs.
 */
internal class NodeSelection(
    private val installed: Map<Int, Path>,
) {
    /** The installation for the project in [repo], whose `engines.node` is [engines]. Fails if no installed Node fits. */
    fun home(
        repo: Path,
        engines: String?,
    ): Path? {
        if (installed.isEmpty()) return null
        val declared = declared(repo)
        val major =
            when {
                declared != null -> major(declared.first, declared.second)
                engines != null -> highest(engines)
                else -> newestLts()
            }
        logger.info("{} runs on Node {}", repo, major)
        return installed.getValue(major)
    }

    /** The version `.nvmrc` or `.node-version` names, and the file's name. Comments and blank lines don't count. */
    private fun declared(repo: Path): Pair<String, String>? {
        val file = VERSION_FILES.map { repo.resolve(it) }.firstOrNull { it.isRegularFile() } ?: return null
        val version = file.readLines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return null
        return version to file.fileName.toString()
    }

    private fun major(
        declared: String,
        file: String,
    ): Int {
        val lts = declared.lowercase().removePrefix("lts/")
        val version = VERSION.matchEntire(declared)
        val major =
            when {
                lts == "*" -> newestLts()
                declared.lowercase().startsWith("lts/") -> CODENAMES[lts] ?: unreadable(declared, file)
                version != null -> version.groupValues[1].toInt()
                else -> unreadable(declared, file)
            }
        if (major !in installed) throw ToolError("The project asks for Node $declared ($file), but only Node $available are installed.")
        return major
    }

    /** The highest even major: the newest LTS line. Without one, the highest. */
    private fun newestLts() = installed.keys.filter { it % 2 == 0 }.maxOrNull() ?: installed.keys.max()

    private fun highest(engines: String): Int {
        val range =
            NpmRange.parse(engines)
                ?: throw ToolError("Can't read engines.node $engines in package.json. lector-ai reads npm ranges, e.g. >=22.")
        return installed.keys.sortedDescending().firstOrNull { range.contains(version(installed.getValue(it))) }
            ?: throw ToolError("The project asks for Node $engines (engines.node in package.json), but only Node $available are installed.")
    }

    private val available get() = installed.keys.sorted().joinToString(", ")

    private fun unreadable(
        declared: String,
        file: String,
    ): Nothing =
        throw ToolError("Can't read the Node version $declared in $file. lector-ai reads a version, a major, lts/*, or an LTS codename.")

    /** The version of the Node in [home], from the header every Node distribution ships. */
    private fun version(home: Path): Semver {
        val header = home.resolve("include/node/node_version.h")
        val defines = if (header.isRegularFile()) header.readLines().mapNotNull { HEADER.matchEntire(it.trim()) } else emptyList()
        val parts = defines.associate { it.groupValues[1] to it.groupValues[2].toInt() }
        val numbers = listOf("MAJOR", "MINOR", "PATCH").mapNotNull { parts[it] }
        if (numbers.size < PARTS) throw ToolError("Can't read the version of the Node in $home: $header is missing or incomplete.")
        return Semver(numbers[0], numbers[1], numbers[2])
    }
}

/**
 * The Node installations for the projects the tool builds, by major, from `LECTOR_NODE_<major>=<installation>` variables
 * in [environment]. The container image sets them.
 */
fun installedNodes(environment: Map<String, String>): Map<Int, Path> =
    environment
        .filterKeys { it.startsWith(NODE_VARIABLE) }
        .mapNotNull { (name, value) -> name.removePrefix(NODE_VARIABLE).toIntOrNull()?.let { it to Path.of(value) } }
        .toMap()

private const val NODE_VARIABLE = "LECTOR_NODE_"
