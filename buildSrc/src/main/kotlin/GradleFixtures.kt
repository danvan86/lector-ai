import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType

/**
 * Sets up a test task that runs real Gradle builds on fixture repositories, the way the tool image does: JDKs 21 and 25
 * as `LECTOR_JDK_<release>` (from Gradle's toolchains, downloaded if missing), and a Gradle user home of its own under
 * the root build directory. Its own, because closing the Gradle adapter stops every daemon of a Gradle version in the
 * user home, the one running this build included. The tests copy the tool's own wrapper (`lector.gradleWrapper`) into
 * the fixtures, which bring only their `gradle-wrapper.properties`.
 */
fun Test.gradleFixtures() {
    val toolchains = project.extensions.getByType<JavaToolchainService>()
    val jdks =
        listOf(21, 25).associateWith { release ->
            toolchains
                .launcherFor { languageVersion.set(JavaLanguageVersion.of(release)) }
                .map { it.metadata.installationPath.asFile.path }
        }
    val userHome =
        project.rootProject.layout.buildDirectory
            .dir("gradle-test-home")
            .get()
            .asFile.path
    environment("GRADLE_USER_HOME", userHome)
    systemProperty("lector.gradleWrapper", project.rootProject.layout.projectDirectory.asFile.path)
    doFirst {
        jdks.forEach { (release, home) -> (this as Test).environment("LECTOR_JDK_$release", home.get()) }
    }
}
