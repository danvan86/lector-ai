package gmbh.vanella.lector.ecosystems.typescript

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readLines

// A Yarn classic entry's version, below its `"@scope/a@^1.0.0", a@1.0.0:` line: `  version "1.2.3"`.
private val CLASSIC_VERSION = Regex("""^\s+version "([^"]+)"$""")

/** How a lockfile resolves one dependency, by the package that declares it, its name, and its range. */
private typealias Resolver = (pkg: NodePackage, name: String, range: String) -> String?

/**
 * The resolved versions of a project's direct dependencies, read from its lockfile without an install (spec section 7).
 * Every format maps a package's own dependency to the version it resolves to; links to workspace packages and local
 * files resolve to none. If packages resolve a dependency to different versions, the highest wins.
 */
internal object Lockfiles {
    fun resolvedVersions(
        repo: Path,
        project: NodeProject,
    ): Map<String, String> {
        val lockfile = repo.resolve(project.manager.lockfile)
        val resolve: Resolver =
            when (project.manager) {
                PackageManager.NPM -> npm(jsonObject(lockfile, lockfile.name))
                PackageManager.PNPM -> pnpm(mapping(yamlDocuments(lockfile, lockfile.name).lastOrNull()))
                PackageManager.YARN_CLASSIC -> classic(lockfile)
                PackageManager.YARN_BERRY -> berry(mapping(yamlDocuments(lockfile, lockfile.name).firstOrNull()))
            }
        val resolved =
            project.packages.flatMap { pkg ->
                pkg.manifest.dependencies.mapNotNull { (name, range) -> resolve(pkg, name, range)?.let { name to it } }
            }
        return resolved
            .filter { (_, version) -> Semver.parse(version) != null }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, versions) -> versions.maxWith(SemverScheme::compare) }
            .toSortedMap()
    }

    /** `package-lock.json` 2 and 3: the package's own `node_modules` first, then the root's. */
    private fun npm(lockfile: JsonObject): Resolver {
        val packages = lockfile["packages"] as? JsonObject
        val version = { path: String -> (packages?.get(path) as? JsonObject)?.string("version") }
        return { pkg, name, _ ->
            (if (pkg.dir.isEmpty()) null else version("${pkg.dir}/node_modules/$name"))
                ?: version("node_modules/$name")
        }
    }

    /** `pnpm-lock.yaml` 9.0: each importer (package directory, `.` for the root) names its dependencies' versions. */
    private fun pnpm(lockfile: Map<*, *>): Resolver {
        val importers = mapping(lockfile["importers"])
        return { pkg, name, _ ->
            val importer = mapping(importers[pkg.dir.ifEmpty { "." }])
            val entry =
                listOf(
                    "dependencies",
                    "devDependencies",
                    "optionalDependencies",
                ).firstNotNullOfOrNull { mapping(importer[it])[name] }
            // `1.0.0(peer@2.0.0)` names the peers it was resolved with; an alias is `real-name@1.0.0`.
            val version = mapping(entry)["version"]
            if (version == null) null else version.toString().substringBefore('(').substringAfterLast('@')
        }
    }

    /** Yarn classic: every entry is keyed by the `name@range` descriptors that resolve to it. */
    private fun classic(lockfile: Path): Resolver {
        val versions = mutableMapOf<String, String>()
        var descriptors = emptyList<String>()
        for (line in lockfile.readLines()) {
            val version = CLASSIC_VERSION.matchEntire(line)
            when {
                !line.startsWith(" ") && line.endsWith(":") -> {
                    descriptors =
                        line.removeSuffix(":").split(", ").map { it.trim().removeSurrounding("\"") }
                }

                version != null -> {
                    descriptors.forEach { versions[it] = version.groupValues[1] }
                }
            }
        }
        return { _, name, range -> versions["$name@$range"] }
    }

    /**
     * Yarn Berry: entries are keyed by descriptors (`name@npm:^1.0.0`). Each workspace's entry lists its dependencies'
     * descriptors, so the package's range never has to be normalized here.
     */
    private fun berry(lockfile: Map<*, *>): Resolver {
        val entries =
            lockfile.entries
                .filter { (key) -> key != "__metadata" }
                .flatMap { (key, entry) -> key.toString().split(", ").map { it.trim() to mapping(entry) } }
                .toMap()
        // The dependencies of each workspace, by its directory: `name@workspace:packages/app` → `packages/app`.
        val workspaces =
            entries.values
                .map { it["resolution"].toString() }
                .filter { "@workspace:" in it }
                .associate { resolution ->
                    resolution.substringAfter("@workspace:") to mapping(mapping(entries[resolution])["dependencies"])
                }
        return { pkg, name, _ ->
            val descriptor = mapping(workspaces[pkg.dir.ifEmpty { "." }])[name] as? String
            // A workspace package (`workspace:*`) is no installed version.
            if (descriptor == null ||
                descriptor.startsWith("workspace:")
            ) {
                null
            } else {
                mapping(entries["$name@$descriptor"])["version"]?.toString()
            }
        }
    }
}
