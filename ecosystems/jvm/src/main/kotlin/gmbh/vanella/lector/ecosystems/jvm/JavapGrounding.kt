package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange
import gmbh.vanella.lector.core.Diagnostic
import gmbh.vanella.lector.core.ProcessResult
import gmbh.vanella.lector.core.runProcess
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
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
    private val import = Regex("""^\s*import\s+(?:static\s+)?([\w.]+?)(\.\*)?(?:\s+as\s+\w+)?\s*;?\s*$""", RegexOption.MULTILINE)
    private val qualifiedClass = Regex("""\b(?:[a-z_][\w]*\.)+[A-Z][\w$]*""")
    private val classEnd = Regex("""^}$""", RegexOption.MULTILINE)

    fun ground(
        repo: Path,
        change: DependencyChange,
        diagnostics: List<Diagnostic>,
    ): String {
        val jars = jars(repo, change)
        val index = jars.flatMap(::classNames).toSet()
        val references = references(repo, diagnostics)
        val direct = references.flatMap { name -> prefixes(name).filter { it in index }.take(1) }.toSet()
        val packages = direct.map(::packageOf).toSet() + references.filter { it.endsWith(".*") }.map { it.removeSuffix(".*") }
        val missing = diagnostics.flatMap { listOfNotNull(ClusterKeys.missingSymbol(it.message), ClusterKeys.missingPackage(it.message)) }
        val symbols = missing.toSet() + moved(references, missing.toSet())
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

    /** Imports of the erroring files (`a.b.C`, `a.b.*`) and qualified class names in the messages. */
    private fun references(
        repo: Path,
        diagnostics: List<Diagnostic>,
    ): Set<String> {
        val files =
            diagnostics
                .mapNotNull { it.file }
                .toSet()
                .map { repo.resolve(it) }
                .filter { it.isRegularFile() }
        val imports = files.flatMap { file -> import.findAll(file.readText()).map { it.groupValues[1] + it.groupValues[2] } }
        val named = diagnostics.flatMap { diagnostic -> qualifiedClass.findAll(diagnostic.message).map { it.value } }
        return (imports + named).toSet()
    }

    /**
     * The simple names of the classes imported from a package that is gone (`mu.KotlinLogging` for kotlinc's 'mu', javac's
     * `org.example.old`): the new version may have them in another package.
     */
    private fun moved(
        references: Set<String>,
        missing: Set<String>,
    ) = references
        .filterNot { it.endsWith(".*") }
        .filter { ref -> prefixes(packageOf(ref)).any { it in missing || simpleName(it) in missing } }
        .map(::simpleName)

    /** `a.b.C.m` → `a.b.C.m`, `a.b.C`, `a.b`, `a`: a static import names a member, so its class is a prefix. */
    private fun prefixes(name: String) = generateSequence(name) { if ('.' in it) it.substringBeforeLast('.') else null }

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

    private fun packageOf(name: String) = name.substringBeforeLast('.', "")

    private fun simpleName(name: String) = name.substringAfterLast('.')
}

/**
 * Every JAR of the package's group at the new version in the local Maven repository at [localRepository], without
 * sources, Javadoc, and test JARs.
 */
internal fun localRepositoryJars(localRepository: Path): (Path, DependencyChange) -> List<Path> =
    { _, change ->
        val group = localRepository.resolve(change.`package`.substringBefore(':').replace('.', '/'))
        if (':' !in change.`package` || !group.isDirectory()) {
            emptyList()
        } else {
            group
                .listDirectoryEntries()
                .map { it.resolve(change.toVersion) }
                .filter { it.isDirectory() }
                .flatMap { it.listDirectoryEntries("*.jar") }
                .filter { jar -> listOf("-sources.jar", "-javadoc.jar", "-tests.jar").none { jar.name.endsWith(it) } }
                .sorted()
        }
    }
