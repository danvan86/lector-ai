package gmbh.vanella.lector.core

/**
 * One dependency update, the same for every ecosystem and every bot.
 *
 * - [ecosystem] is the package namespace (`npm`, `pypi`, one for all JVM builds), not the build tool.
 * - [package] is an opaque string. Only the ecosystem's adapter knows its shape.
 * - [updateType] is derived from the versions by the ecosystem's [VersionScheme].
 */
data class DependencyChange(
    val ecosystem: String,
    val `package`: String,
    val fromVersion: String,
    val toVersion: String,
    val updateType: UpdateType,
)

/** Which part of the version changed. [OTHER] covers everything else, e.g. only a qualifier. */
enum class UpdateType {
    MAJOR,
    MINOR,
    PATCH,
    OTHER,
}
