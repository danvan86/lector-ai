package gmbh.vanella.lector.ecosystems.typescript

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.EcosystemAdapter
import gmbh.vanella.lector.core.LanguageSupport
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.ResolvedVersions
import gmbh.vanella.lector.core.runProcess
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.time.Duration

/**
 * Builds and tests TypeScript repositories on npm, pnpm, or Yarn (classic and Berry, Plug'n'Play included), with or
 * without workspaces (M9). It speaks for the `npm` ecosystem and has no recipe runner: OpenRewrite's JavaScript recipes
 * are MSAL-licensed and only run in the Moderne CLI, so verify leads straight to the fix loop (spec section 7).
 *
 * The package managers run through Corepack, which honours the project's `packageManager`. Node comes from the
 * installed [nodes] (major → installation, from `LECTOR_NODE_<major>`). [npmrc] and [yarnrc] are the user-level
 * registry configuration of every run, [jestJunit] the image's `jest-junit` that Jest reports through. Of the tool's
 * [environment], the processes get the package managers' variables.
 */
class TypeScriptAdapter(
    npmrc: Path? = null,
    yarnrc: Path? = null,
    nodes: Map<Int, Path> = emptyMap(),
    environment: Map<String, String> = emptyMap(),
    jestJunit: Path? = null,
    run: (command: List<String>, workDir: Path, environment: Map<String, String>, timeout: Duration) -> ProcessResult =
        { command, workDir, variables, timeout ->
            runProcess(
                listOf(executable(command.first(), variables["PATH"] ?: environment["PATH"])) + command.drop(1),
                workDir,
                timeout,
                variables,
            )
        },
) : EcosystemAdapter,
    AutoCloseable {
    private val selection = NodeSelection(nodes)
    private val build = NodeBuild(selection, environment, npmrc, yarnrc, jestJunit, run)
    private val grounding = DeclarationGrounding(::installedPackages)

    override val ecosystem = ECOSYSTEM
    override val versionScheme = SemverScheme

    // OpenRewrite JavaScript is MSAL-licensed and runs only in the Moderne CLI (spec Q10).
    override val recipeRunner = null

    /** The resolved versions of the direct dependencies, from the lockfile: no install needed, on the base commit neither. */
    override val resolvedVersions = ResolvedVersions { repo -> Lockfiles.resolvedVersions(repo, NodeProject.read(repo)) }

    override val languageSupport: LanguageSupport = TypeScriptLanguageSupport

    /**
     * Whether [repo] has a `package.json` and a `tsconfig.json` at its root. Fails with a [gmbh.vanella.lector.core.ToolError]
     * for a TypeScript repository this adapter can't build (see [NodeProject.read]), or one that asks for a Node that
     * isn't installed.
     */
    override fun detect(repo: Path): Boolean {
        if (!NodeProject.detect(repo)) return false
        selection.home(
            repo,
            NodeProject
                .read(repo)
                .root.manifest.engines,
        )
        return true
    }

    override fun verify(
        repo: Path,
        timeout: Duration,
    ) = build.verify(repo, timeout)

    override fun testsRun(repo: Path) = build.testsRun(repo)

    override fun runTests(
        repo: Path,
        tests: List<Diagnostic>,
        timeout: Duration,
    ) = build.runTests(repo, tests, timeout)

    override fun ground(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ) = grounding.ground(repo, change, diagnostics)

    override fun clusterKey(diagnostic: Diagnostic) = TypeScriptClusterKeys.of(diagnostic)

    override fun isBuildFile(file: String) = TypeScriptScope.isBuildFile(file)

    override fun isEditable(file: String) = TypeScriptScope.isEditable(file)

    override fun testDisablingConfig(
        file: String,
        content: String,
        line: Int,
    ) = TypeScriptTestSkipping.reason(file, content, line)

    override fun loosening(
        file: String,
        before: String,
        after: String,
    ) = TypeScriptLoosening.of(file, before, after)

    override fun updateLockfiles(
        repo: Path,
        timeout: Duration,
    ) = build.updateLockfiles(repo, timeout)

    override fun close() = build.close()

    /** The installed packages [names] an importer in [issuer] sees: through Plug'n'Play, or in the nearest `node_modules`. */
    private fun installedPackages(
        repo: Path,
        issuer: Path,
        names: List<String>,
    ): Map<String, PackageFiles> {
        if (repo.resolve(".pnp.cjs").isRegularFile()) return build.pnpLocations(repo, issuer, names).mapValues { PackageFiles.at(it.value) }
        val dirs = generateSequence(issuer) { it.parent }.takeWhile { it.startsWith(repo) }.toList()
        return names
            .mapNotNull { name ->
                dirs.map { it.resolve("node_modules").resolve(name) }.firstOrNull { it.isDirectory() }?.let {
                    name to
                        PackageFiles.at(it.toString())
                }
            }.toMap()
    }
}

/** The package registry this adapter speaks for, as Renovate and Dependabot name it. */
internal const val ECOSYSTEM = "npm"

/** The program [name] in the directories of [path]: the child process's `PATH` decides, not the tool's. */
internal fun executable(
    name: String,
    path: String?,
): String {
    if ('/' in name || path == null) return name
    val found =
        path.split(File.pathSeparator).filter { it.isNotEmpty() }.map { Path.of(it, name) }.firstOrNull {
            it.isRegularFile() &&
                it.isExecutable()
        }
    return if (found == null) name else found.toString()
}
