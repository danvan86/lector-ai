package gmbh.vanella.lector.benchmark

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The origin of a case whose head commit was made for the benchmark, not by a bot. */
const val SYNTHETIC = "synthetic"

/** The JVM's ecosystem, whose numbers the report shows first. */
const val MAVEN = "maven"

// The ecosystems the harness builds, and the report reports separately.
private val ECOSYSTEMS = setOf(MAVEN, "npm")

private const val RESOURCE = "corpus.json"
private val ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")
private val COMMIT = Regex("[0-9a-f]{40}")
private val PULL_REQUEST = Regex("https://github\\.com/[^/]+/[^/]+/pull/\\d+")

/** The benchmark's cases: real dependency updates whose head commit has a failure the update caused (spec section 1). */
@Serializable
data class Corpus(
    val cases: List<Case>,
) {
    /** The case with [id]. Fails if there is none. */
    fun case(id: String) = requireNotNull(cases.firstOrNull { it.id == id }) { "No case $id in the corpus" }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        /** The corpus that ships with the benchmark. */
        fun load(resource: String = RESOURCE): Corpus {
            val stream = checkNotNull(Corpus::class.java.getResourceAsStream(resource)) { "$resource is missing from the classpath" }
            return parse(stream.use { it.readBytes().decodeToString() })
        }

        /** Fails with an [IllegalArgumentException] if [text] isn't a valid corpus. */
        fun parse(text: String): Corpus {
            val corpus =
                try {
                    json.decodeFromString<Corpus>(text)
                } catch (e: SerializationException) {
                    throw IllegalArgumentException("Malformed corpus: ${e.message}", e)
                }
            corpus.cases.forEach(::check)
            val duplicates =
                corpus.cases
                    .groupBy { it.id }
                    .filterValues { it.size > 1 }
                    .keys
            require(duplicates.isEmpty()) { "Duplicate case IDs: ${duplicates.joinToString(", ")}" }
            return corpus
        }

        private fun check(case: Case) {
            require(ID.matches(case.id)) { "ID ${case.id} is not lower-case kebab-case" }
            require(
                case.origin == SYNTHETIC || PULL_REQUEST.matches(case.origin),
            ) { "${case.id}: origin is neither a PR URL nor $SYNTHETIC" }
            require(case.repository.startsWith("https://")) { "${case.id}: repository is not an https URL" }
            require(COMMIT.matches(case.base)) { "${case.id}: base is not a full commit hash" }
            require(COMMIT.matches(case.head)) { "${case.id}: head is not a full commit hash" }
            require(case.changes.isNotEmpty()) { "${case.id}: no changes" }
            require(case.ecosystem in ECOSYSTEMS) { "${case.id}: ecosystem ${case.ecosystem} is none of ${ECOSYSTEMS.joinToString(", ")}" }
        }
    }
}

/**
 * One dependency update: a historical bot PR ([origin] is its URL) or a [SYNTHETIC] bump. [base] is the commit before
 * the update, [head] the commit with it, both in [repository]. [note] says what breaks, for the curator, or is null.
 * [ecosystem] is the package namespace of its changes, which the report groups the numbers by (M9).
 */
@Serializable
data class Case(
    val id: String,
    val origin: String,
    val repository: String,
    val base: String,
    val head: String,
    val changes: List<Change>,
    val note: String?,
    val ecosystem: String,
)

/** One package the case's update moves, in its ecosystem's notation. */
@Serializable
data class Change(
    val `package`: String,
    val from: String,
    val to: String,
)
