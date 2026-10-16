package gmbh.vanella.lector.ecosystems.typescript

/**
 * The command lines of a package manager (spec sections 3 and 4). Every tool is the project's own, run through the
 * package manager in the package's directory. npm never fetches a missing tool (`--no`), and Yarn Berry runs a tool
 * that only the root depends on from the root (`-T`), because Plug'n'Play hoists nothing.
 */
internal class Commands(
    private val manager: PackageManager,
) {
    /** The install: frozen on the PR's head, so the lockfile is what the project's CI installs; not frozen after a build-file edit. */
    fun install(frozen: Boolean): List<String> =
        when (manager) {
            PackageManager.NPM -> if (frozen) listOf("npm", "ci") else listOf("npm", "install")
            PackageManager.PNPM -> listOf("pnpm", "install", if (frozen) "--frozen-lockfile" else "--no-frozen-lockfile")
            PackageManager.YARN_CLASSIC -> listOf("yarn", "install") + if (frozen) listOf("--frozen-lockfile") else emptyList()
            PackageManager.YARN_BERRY -> listOf("yarn", "install", if (frozen) "--immutable" else "--no-immutable")
        }

    /** Runs the package's script [name]. */
    fun script(name: String) = listOf(manager.command, "run", name)

    /** Runs the package's `test` script. */
    fun test() = listOf(manager.command, "test")

    /** Runs the binary [bin] of the package [provider], which [pkg] or the root depends on, with [arguments]. */
    fun exec(
        pkg: NodePackage,
        provider: String,
        bin: String,
        arguments: List<String>,
    ): List<String> =
        when (manager) {
            PackageManager.NPM -> listOf("npm", "exec", "--no", "--", bin)
            PackageManager.PNPM -> listOf("pnpm", "exec", bin)
            PackageManager.YARN_CLASSIC -> listOf("yarn", "run", bin)
            PackageManager.YARN_BERRY -> listOf("yarn", "run") + (if (pkg.dependsOn(provider)) emptyList() else listOf("-T")) + bin
        } + arguments
}
