package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.ToolError
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.visitFileTree

internal const val PACKAGE_JSON = "package.json"
internal const val TSCONFIG = "tsconfig.json"

// The tool behind each lockfile, as messages name it, and its command.
private val MANAGERS =
    mapOf(
        PackageManager.NPM.lockfile to ("npm" to PackageManager.NPM.command),
        PackageManager.PNPM.lockfile to ("pnpm" to PackageManager.PNPM.command),
        PackageManager.YARN_BERRY.lockfile to ("Yarn" to PackageManager.YARN_BERRY.command),
    )

// The oldest lockfile formats of the maintained package manager majors (checked October 2026).
private const val OLDEST_NPM_LOCKFILE = 2
private const val OLDEST_PNPM_LOCKFILE = 9
private const val OLDEST_BERRY_LOCKFILE = 8

/**
 * One package of the repository: the root (`dir` is empty) or a workspace package, `dir` relative to the root with `/`
 * as separator.
 */
internal data class NodePackage(
    val dir: String,
    val manifest: PackageJson,
    val hasTsconfig: Boolean,
) {
    /** This package's directory in the repository at [root]. */
    fun dir(root: Path): Path = if (dir.isEmpty()) root else root.resolve(dir)

    /** [file] of this package, relative to the repository root. */
    fun relative(file: String) = if (dir.isEmpty()) file else "$dir/$file"

    /** How messages name the package. */
    val label get() = dir.ifEmpty { "The root package" }

    /** Whether the package itself depends on [name]. */
    fun dependsOn(name: String) = name in manifest.dependencies
}

/**
 * A TypeScript repository as its package manager sees it: the [manager] its lockfile belongs to, and its [packages],
 * the root first, then the workspace packages in path order.
 */
internal class NodeProject(
    val manager: PackageManager,
    val packages: List<NodePackage>,
) {
    val root get() = packages.first()

    companion object {
        /** Whether [repo] has a `package.json` and a `tsconfig.json` at its root. */
        fun detect(repo: Path) = repo.resolve(PACKAGE_JSON).isRegularFile() && repo.resolve(TSCONFIG).isRegularFile()

        /**
         * Reads the project in [repo]. Fails with a [ToolError] for one this adapter doesn't build: no lockfile or several,
         * a lockfile format older than the maintained majors of its manager, Yarn Berry without a pinned Yarn, or a
         * package with a `tsconfig.json` but no `typescript` to check it with.
         */
        fun read(repo: Path): NodeProject {
            val root = PackageJson.read(repo.resolve(PACKAGE_JSON), PACKAGE_JSON)
            val manager = manager(repo, root)
            val patterns = if (manager == PackageManager.PNPM) pnpmWorkspaces(repo) else root.workspaces
            val packages =
                listOf(NodePackage("", root, repo.resolve(TSCONFIG).isRegularFile())) +
                    workspaceDirs(repo, patterns).map { dir ->
                        val label = "$dir/$PACKAGE_JSON"
                        NodePackage(dir, PackageJson.read(repo.resolve(label), label), repo.resolve(dir).resolve(TSCONFIG).isRegularFile())
                    }
            requireTypeScript(packages)
            return NodeProject(manager, packages)
        }

        private fun manager(
            repo: Path,
            root: PackageJson,
        ): PackageManager {
            val lockfiles =
                PackageManager.entries
                    .map { it.lockfile }
                    .distinct()
                    .filter { repo.resolve(it).isRegularFile() }
            if (lockfiles.isEmpty()) {
                throw ToolError(
                    "$repo has no lockfile. lector-ai installs exactly what the lockfile says: commit a package-lock.json, " +
                        "pnpm-lock.yaml, or yarn.lock.",
                )
            }
            if (lockfiles.size > 1) {
                throw ToolError(
                    "$repo has more than one lockfile (${lockfiles.joinToString(", ")}). lector-ai doesn't pick a package " +
                        "manager: remove all but one.",
                )
            }
            val lockfile = repo.resolve(lockfiles.single())
            val pinned = root.packageManager
            val command = checkPinned(lockfile, pinned)
            return when (command) {
                PackageManager.NPM.command -> PackageManager.NPM.also { checkNpm(lockfile) }
                PackageManager.PNPM.command -> PackageManager.PNPM.also { checkPnpm(lockfile) }
                else -> yarn(repo, lockfile, pinned)
            }
        }

        private fun checkNpm(lockfile: Path) {
            val version = (jsonObject(lockfile, lockfile.name)["lockfileVersion"] as? JsonPrimitive)?.intOrNull ?: 1
            if (version < OLDEST_NPM_LOCKFILE) {
                throw ToolError(
                    "${lockfile.name} has lockfileVersion $version, written by npm 6 or older. lector-ai supports lockfileVersion " +
                        "2 and 3 (npm 7 and newer).",
                )
            }
        }

        private fun checkPnpm(lockfile: Path) {
            val version = mapping(yamlDocuments(lockfile, lockfile.name).lastOrNull())["lockfileVersion"].toString()
            val major = version.substringBefore('.').toIntOrNull() ?: 0
            if (major < OLDEST_PNPM_LOCKFILE) {
                throw ToolError(
                    "${lockfile.name} has lockfileVersion $version, written by pnpm 8 or older. lector-ai supports lockfileVersion " +
                        "9.0 (pnpm 9 and newer).",
                )
            }
        }

        /** Yarn classic or Berry, by the lockfile's format. Berry must name its version: Corepack's default is classic. */
        private fun yarn(
            repo: Path,
            lockfile: Path,
            pinned: String?,
        ): PackageManager {
            if ("# yarn lockfile v1" in lockfile.readText()) return PackageManager.YARN_CLASSIC
            val metadata = berryMetadata(lockfile)
            val version = metadata["version"].toString().toIntOrNull() ?: 0
            if (version < OLDEST_BERRY_LOCKFILE) {
                throw ToolError(
                    "${lockfile.name} has __metadata.version $version, written by Yarn 3 or older. lector-ai supports version 8 " +
                        "and newer (Yarn 4).",
                )
            }
            val yarnrc = repo.resolve(".yarnrc.yml")
            val yarnPath = yarnrc.isRegularFile() && mapping(yamlDocuments(yarnrc, yarnrc.name).firstOrNull())["yarnPath"] != null
            if (pinned == null && !yarnPath) {
                throw ToolError(
                    "${lockfile.name} is Yarn Berry's, but package.json names no packageManager and .yarnrc.yml no yarnPath. " +
                        "lector-ai doesn't guess the Yarn version: add \"packageManager\": \"yarn@<version>\" to package.json.",
                )
            }
            return PackageManager.YARN_BERRY
        }

        private fun berryMetadata(lockfile: Path): Map<*, *> {
            val metadata = mapping(yamlDocuments(lockfile, lockfile.name).firstOrNull())["__metadata"]
            return metadata as? Map<*, *>
                ?: throw ToolError(
                    "Can't read ${lockfile.name}: it is neither Yarn classic's format (`# yarn lockfile v1`) nor Yarn Berry's (`__metadata`)",
                )
        }

        private fun pnpmWorkspaces(repo: Path): List<String> {
            val file = repo.resolve("pnpm-workspace.yaml")
            if (!file.isRegularFile()) return emptyList()
            val packages = mapping(yamlDocuments(file, file.name).firstOrNull())["packages"] as? List<*>
            return packages.orEmpty().filterIsInstance<String>()
        }

        /**
         * The directories with a `package.json` that the workspace [patterns] name, relative to [repo], sorted. `!` excludes;
         * `node_modules` and dot directories are never searched.
         */
        private fun workspaceDirs(
            repo: Path,
            patterns: List<String>,
        ): List<String> {
            if (patterns.isEmpty()) return emptyList()
            val (excluded, included) = patterns.map { it.trim() }.partition { it.startsWith("!") }
            val matchers = { globs: List<String> ->
                globs.map {
                    val glob =
                        it
                            .removePrefix("!")
                            .trim()
                            .removePrefix("./")
                            .removeSuffix("/")
                    FileSystems.getDefault().getPathMatcher("glob:$glob")
                }
            }
            val include = matchers(included)
            val exclude = matchers(excluded)
            return packageDirs(repo)
                .filter { dir -> include.any { it.matches(dir) } && exclude.none { it.matches(dir) } }
                .map { it.invariantSeparatorsPathString }
                .sorted()
        }

        /**
         * Every directory below [repo] with a `package.json`, relative to it. `node_modules` and dot directories are never
         * searched: the package managers' globs don't enter them.
         */
        @OptIn(ExperimentalPathApi::class)
        private fun packageDirs(repo: Path): List<Path> {
            val dirs = mutableListOf<Path>()
            repo.visitFileTree {
                onPreVisitDirectory { dir, _ ->
                    val skipped = dir != repo && (dir.name == "node_modules" || dir.name.startsWith("."))
                    if (!skipped && dir != repo && dir.resolve(PACKAGE_JSON).exists()) dirs.add(repo.relativize(dir))
                    if (skipped) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                }
            }
            return dirs
        }

        private fun requireTypeScript(packages: List<NodePackage>) {
            if (packages.first().dependsOn("typescript")) return
            val missing = packages.firstOrNull { it.hasTsconfig && !it.dependsOn("typescript") } ?: return
            throw ToolError(
                "${missing.label} has a tsconfig.json, but neither it nor the root package.json depends on typescript. " +
                    "lector-ai type-checks with the project's own tsc.",
            )
        }
    }
}

/** The command of [lockfile]'s package manager. Fails if `packageManager` ([pinned]) names another one. */
private fun checkPinned(
    lockfile: Path,
    pinned: String?,
): String {
    val (tool, command) = MANAGERS.getValue(lockfile.name)
    if (pinned != null && !pinned.startsWith("$command@")) {
        throw ToolError(
            "${lockfile.name} is $tool's, but package.json's packageManager is $pinned. lector-ai doesn't pick a package " +
                "manager: make them agree.",
        )
    }
    return command
}
