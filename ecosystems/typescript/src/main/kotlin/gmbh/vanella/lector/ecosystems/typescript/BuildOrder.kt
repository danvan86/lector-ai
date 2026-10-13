package gmbh.vanella.lector.ecosystems.typescript

/**
 * The workspace packages whose `build` script runs before the type check (spec section 4): those another package of
 * the repository depends on, because it imports them through their build output. Dependencies come first. A cycle is
 * broken where it is found. Packages nobody depends on aren't built.
 */
internal fun dependencyBuilds(project: NodeProject): List<NodePackage> {
    val workspaces =
        project.packages
            .drop(1)
            .filter { it.manifest.name != null }
            .associateBy { it.manifest.name }
    val dependsOn = { pkg: NodePackage ->
        pkg.manifest.dependencies.keys
            .mapNotNull { workspaces[it] }
            .filter { it != pkg }
    }
    val dependedOn = project.packages.flatMap(dependsOn).toSet()
    val ordered = linkedSetOf<NodePackage>()
    val visiting = mutableSetOf<NodePackage>()

    fun visit(pkg: NodePackage) {
        if (pkg in ordered || !visiting.add(pkg)) return
        dependsOn(pkg).forEach(::visit)
        ordered += pkg
    }
    workspaces.values.filter { it in dependedOn }.forEach(::visit)
    return ordered.filter { "build" in it.manifest.scripts }
}
