package gmbh.vanella.lector.ecosystems.typescript

/** The package managers lector-ai runs, by the lockfile they write. Yarn classic (1.x) and Berry (2 and newer) differ in everything. */
internal enum class PackageManager(
    val command: String,
    val lockfile: String,
) {
    NPM("npm", "package-lock.json"),
    PNPM("pnpm", "pnpm-lock.yaml"),
    YARN_CLASSIC("yarn", "yarn.lock"),
    YARN_BERRY("yarn", "yarn.lock"),
}
