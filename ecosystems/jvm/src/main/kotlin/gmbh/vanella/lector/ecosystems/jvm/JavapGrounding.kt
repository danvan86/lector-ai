package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.ToolError
import gmbh.vanella.lector.core.runProcess
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.time.Duration.Companion.minutes

private val logger = LoggerFactory.getLogger(JavapGrounding::class.java)
private val JAVAP_TIMEOUT = 2.minutes

// Enough for every class of a few packages. More would only be trimmed from the request.
private const val MAX_CLASSES = 300

/**
 * The public API of the new version, from `javap -public` on the JARs the build already resolved: every artifact of the
 * updated package's group at the new version, so a release train (Spring Boot, Jackson) is covered too. [jars] finds
 * them for a change in a repository, e.g. in the local Maven repository ([localRepositoryJars]). Nothing is downloaded.
 *
 * Shown are the classes the errors refer to: the erroring files' imports and the classes their messages name, plus
 * the classes that declare the missing symbol, in those packages or, for a class, anywhere in the JARs. A class imported
 * from a package that is gone is a missing class too.
 */
internal class JavapGrounding(
    private val jars: (repo: Path, change: DependencyChange) -> List<Path>,
    private val run: (command: List<String>, workDir: Path) -> ProcessResult = { command, workDir ->
        runProcess(command, workDir, JAVAP_TIMEOUT)
    },
) {
    private val classEnd = Regex("""^}$""", RegexOption.MULTILINE)

    /** The full API, or none if a JAR can't be read or javap can't run: grounding never ends the run. */
    fun ground(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ): String =
        try {
            javapApi(repo, change, diagnostics)
        } catch (e: IOException) {
            logger.info("No API of {}: can't read a JAR: {}", change.`package`, e.message)
            ""
        } catch (e: ToolError) {
            logger.warn("No API of {}: {}", change.`package`, e.message)
            ""
        }

    private fun javapApi(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ): String {
        val jars = jars(repo, change)
        val index = jars.flatMap(::classNames).toSet()
        val references = ErrorReferences(repo, diagnostics)
        val direct = references.classes(references.all, index)
        val packages = direct.map(::packageOf).toSet() + references.all.filter { it.endsWith(".*") }.map { it.removeSuffix(".*") }
        val symbols = references.symbols
        val candidates = index.filter { packageOf(it) in packages || simpleName(it) in symbols }
        val classes = (direct + candidates).sorted().take(MAX_CLASSES)
        if (classes.isEmpty()) return ""
        val blocks =
            javap(repo, jars, classes).filter { (name, block) ->
                name in direct || simpleName(name) in symbols || symbols.any { Regex("""\b${Regex.escape(it)}\(""") in block }
            }
        if (blocks.isEmpty()) return ""
        return "javap -public of ${change.`package`} ${change.toVersion}:\n\n" + blocks.joinToString("\n\n") { it.second }
    }

    private fun classNames(jar: Path): List<String> =
        ZipFile(jar.toFile()).use { zip ->
            zip
                .entries()
                .asSequence()
                .map { it.name }
                .filter { it.endsWith(".class") && '$' !in it && !it.endsWith("-info.class") }
                .map { it.removeSuffix(".class").replace('/', '.') }
                .toList()
        }

    /** One `(class, signatures)` pair per class, without the `Compiled from` line. */
    private fun javap(
        repo: Path,
        jars: List<Path>,
        classes: List<String>,
    ): List<Pair<String, String>> {
        val classpath = jars.joinToString(File.pathSeparator)
        val result = run(listOf("javap", "-public", "-cp", classpath) + classes, repo)
        if (result.exitCode != 0) logger.warn("javap failed with exit code {}: {}", result.exitCode, result.output.lines().take(1))
        // Each class ends with a `}` line. Lines before its header are noise: `Compiled from`, errors, blank lines.
        return result.output.split(classEnd).mapNotNull { chunk ->
            val lines = chunk.lines().dropWhile { !it.endsWith("{") }
            lines.firstOrNull()?.let { header -> className(header) to (lines.dropLastWhile { it.isBlank() } + "}").joinToString("\n") }
        }
    }

    /** `public final class a.B<T> extends c.D implements e.F {` → `a.B` */
    private fun className(header: String) =
        header
            .substringBefore(" extends ")
            .substringBefore(" implements ")
            .substringBefore('<')
            .substringBefore('(')
            .removeSuffix(" {")
            .substringAfterLast(' ')
}

/**
 * Every JAR of the package's group at the new version in the local Maven repository at [localRepository], without
 * sources, Javadoc, and test JARs.
 */
internal fun localRepositoryJars(localRepository: Path): (Path, DependencyChange) -> List<Path> =
    { _, change ->
        if (':' !in change.`package`) {
            emptyList()
        } else {
            localRepositoryArtifacts(localRepository, change.`package`.substringBefore(':'), change.toVersion).values.flatten().sorted()
        }
    }

/** The JARs of every artifact of [group] at [version] in the local Maven repository at [localRepository], by artifact ID. */
internal fun localRepositoryArtifacts(
    localRepository: Path,
    group: String,
    version: String,
): Map<String, List<Path>> {
    val dir = localRepository.resolve(group.replace('.', '/'))
    if (!dir.isDirectory()) return emptyMap()
    return dir
        .listDirectoryEntries()
        .map { it.resolve(version) }
        .filter { it.isDirectory() }
        .associate { versionDir ->
            versionDir.parent.name to
                versionDir
                    .listDirectoryEntries("*.jar")
                    .filter { jar -> listOf("-sources.jar", "-javadoc.jar", "-tests.jar").none { jar.name.endsWith(it) } }
                    .sorted()
        }.filterValues { it.isNotEmpty() }
}
