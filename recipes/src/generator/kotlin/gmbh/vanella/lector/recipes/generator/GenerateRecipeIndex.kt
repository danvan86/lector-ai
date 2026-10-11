package gmbh.vanella.lector.recipes.generator

import gmbh.vanella.lector.recipes.IndexBuilder
import gmbh.vanella.lector.recipes.IndexFormat
import gmbh.vanella.lector.recipes.RecipeArtifact
import gmbh.vanella.lector.recipes.RecipeTree
import gmbh.vanella.lector.recipes.ScannedRecipe
import org.openrewrite.config.Environment
import org.openrewrite.config.RecipeDescriptor
import org.openrewrite.semver.ExactVersion
import org.openrewrite.semver.Semver
import org.openrewrite.semver.XRange
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val UNKNOWN_LICENSE = "unknown"

private val logger = LoggerFactory.getLogger("gmbh.vanella.lector.recipes.generator.GenerateRecipeIndex")

/**
 * Runs at build time (`generateRecipeIndex`): loads every recipe of the OpenRewrite JARs on the classpath and writes
 * `recipe-index.json` and `recipe-index-report.md` to the directory in `args[0]`. `args[1]` names the BOM. The other
 * arguments are the classpath's JARs with their coordinates, `<path>=<group>:<name>:<version>`.
 */
fun main(args: Array<String>) {
    val (outputDir, bom) = args
    val artifacts = recipeJars(args.drop(2))
    val classLoader = Thread.currentThread().contextClassLoader
    // One environment per JAR lists exactly that JAR's recipes. The others are there to resolve recipes it includes.
    val recipes =
        artifacts.flatMap { (jar, artifact) ->
            Environment
                .builder()
                .scanJar(jar, artifacts.keys - jar, classLoader)
                .build()
                .listRecipeDescriptors()
                .map { ScannedRecipe(tree(it), artifact) }
        }
    val index = IndexBuilder(::skipReason).build(recipes)
    val dir = Path(outputDir).createDirectories()
    dir.resolve("recipe-index.json").writeText(IndexFormat.write(index.entries))
    dir.resolve("recipe-index-report.md").writeText(index.report.render(bom))
    logger.info("Indexed {} recipes with {} entries", index.report.recipes, index.report.entries)
}

/**
 * The OpenRewrite JARs, by path, with their coordinates. The license comes from the JAR's manifest
 * (`License-Name: Moderne Source Available License`). The coordinates come from Gradle, not from the manifest: some
 * manifests name a snapshot version.
 */
private fun recipeJars(jars: List<String>): Map<Path, RecipeArtifact> =
    jars
        .map { it.substringBeforeLast('=') to it.substringAfterLast('=') }
        .filter { (_, coordinates) -> coordinates.startsWith("org.openrewrite") }
        .associate { (path, coordinates) -> Path(path) to RecipeArtifact(coordinates, license(Path(path))) }

private fun license(jar: Path): String {
    val manifest = JarFile(jar.toFile()).use { it.manifest } ?: return UNKNOWN_LICENSE
    return manifest.mainAttributes.getValue("License-Name") ?: UNKNOWN_LICENSE
}

private fun tree(descriptor: RecipeDescriptor): RecipeTree =
    RecipeTree(
        descriptor.name,
        descriptor.options.associate { it.name to it.value?.toString() },
        descriptor.recipeList.map(::tree),
    )

/**
 * OpenRewrite's own reading of a selector. Only exact versions and X-ranges are bounded and supported. A version pattern
 * (`-jre`) narrows the selector, and the index has no field for it: OpenRewrite reads an exact version with a pattern
 * as its own type, an X-range with a pattern is skipped here.
 */
fun skipReason(
    selector: String,
    versionPattern: String?,
): String? {
    val validated = Semver.validate(selector, versionPattern)
    return when (val comparator = if (validated.isValid) validated.value else null) {
        null -> "invalid selector"
        is ExactVersion -> null
        is XRange -> if (versionPattern == null) null else "XRange with versionPattern"
        else -> comparator::class.simpleName
    }
}
