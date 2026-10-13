package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.ToolError
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader

private val logger = LoggerFactory.getLogger(DaemonJdk::class.java)

private const val WRAPPER_PROPERTIES = "gradle/wrapper/gradle-wrapper.properties"
private const val DAEMON_JVM_PROPERTIES = "gradle/gradle-daemon-jvm.properties"

// (first Gradle version, newest Java it runs on), newest first, from Gradle's compatibility matrix. Gradle 8.0 is the
// oldest supported version.
private val NEWEST_JAVA =
    listOf(
        GradleVersion(9, 1) to 25,
        GradleVersion(8, 14) to 24,
        GradleVersion(8, 10) to 23,
        GradleVersion(8, 8) to 22,
        GradleVersion(8, 5) to 21,
        GradleVersion(8, 3) to 20,
        GradleVersion(0, 0) to 19,
    )

// `…/gradle-9.1.0-bin.zip`, `…/gradle-8.10-all.zip`, `…/gradle-9.0.0-rc-1-bin.zip`
private val DISTRIBUTION = Regex("""gradle-((\d+)\.(\d+)(?:\.\d+)?(?:-[\w.-]+)?)-(?:bin|all)\.zip$""")

/** A Gradle version as the wrapper names it, compared by major and minor version. */
internal data class GradleVersion(
    val major: Int,
    val minor: Int,
    val text: String = "$major.$minor",
) : Comparable<GradleVersion> {
    override fun compareTo(other: GradleVersion) = compareValuesBy(this, other, { it.major }, { it.minor })

    override fun toString() = text
}

/** The Gradle version a repository's wrapper runs, from `distributionUrl` in its wrapper properties. */
internal object GradleWrapper {
    fun version(root: Path): GradleVersion {
        val url =
            properties(root.resolve(WRAPPER_PROPERTIES))?.getProperty("distributionUrl")
                ?: throw ToolError("$WRAPPER_PROPERTIES is missing or has no distributionUrl")
        val match =
            DISTRIBUTION.find(url) ?: throw ToolError("Can't read the Gradle version from distributionUrl in $WRAPPER_PROPERTIES: $url")
        val (text, major, minor) = match.destructured
        return GradleVersion(major.toInt(), minor.toInt(), text)
    }
}

/**
 * Picks the JDK that runs Gradle from the [installed] ones (release → `JAVA_HOME`): the one the project asks for in
 * `gradle/gradle-daemon-jvm.properties`, else the newest one its Gradle version runs on. The project's toolchains pick
 * the JDK that compiles, so this one only has to run Gradle. Without installed JDKs, nothing is picked and the
 * environment's `JAVA_HOME` applies.
 */
internal class DaemonJdk(
    private val installed: Map<Int, Path>,
) {
    /** The `JAVA_HOME` for the Gradle [version] of the project at [root], or null to keep the environment's. */
    fun javaHome(
        root: Path,
        version: GradleVersion,
    ): Path? {
        if (installed.isEmpty()) return null
        val requested = properties(root.resolve(DAEMON_JVM_PROPERTIES))?.getProperty("toolchainVersion")?.let { it.trim().toIntOrNull() }
        val jdk =
            if (requested != null) {
                if (requested !in installed) {
                    throw ToolError(
                        "The project runs Gradle on Java $requested ($DAEMON_JVM_PROPERTIES), but only JDKs ${names()} are installed.",
                    )
                }
                requested
            } else {
                val newest = newestJava(version)
                installed.keys.filter { it <= newest }.maxOrNull()
                    ?: throw ToolError("Gradle $version runs on Java $newest or older, but only JDKs ${names()} are installed.")
            }
        logger.info("Running Gradle {} in {} on JDK {}", version, root, jdk)
        return installed.getValue(jdk)
    }

    private fun names() = installed.keys.sorted().joinToString(", ")

    /** The newest Java that Gradle [version] runs on, from Gradle's compatibility matrix. */
    private fun newestJava(version: GradleVersion) = NEWEST_JAVA.first { (gradle) -> version >= gradle }.second
}

private fun properties(file: Path): Properties? = if (file.isRegularFile()) file.reader().use { Properties().apply { load(it) } } else null
