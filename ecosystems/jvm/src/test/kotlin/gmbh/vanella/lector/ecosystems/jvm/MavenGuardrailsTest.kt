package gmbh.vanella.lector.ecosystems.jvm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The adapter's part of the scope and build-file guardrails. */
class MavenGuardrailsTest {
    private val adapter = MavenAdapter(plugins = MavenPlugins("rewrite", "help")) { _, _ -> error("no Maven here") }

    @Test
    fun `sources, tests, and build files are editable`() {
        assertTrue(adapter.isEditable("src/main/java/com/example/App.java"))
        assertTrue(adapter.isEditable("app/src/test/kotlin/com/example/AppTest.kt"))
        assertTrue(adapter.isEditable("pom.xml"))
        assertTrue(adapter.isEditable("src/main/java/com/example/build/Builder.java"), "a package named build is source")
    }

    @Test
    fun `build output and generated code are not`() {
        assertFalse(adapter.isEditable("target/classes/App.class"))
        assertFalse(adapter.isEditable("app/target/generated-sources/Api.java"))
        assertFalse(adapter.isEditable("build/tmp/App.java"))
        assertFalse(adapter.isEditable("src/main/generated/com/example/Api.java"))
        assertFalse(adapter.isEditable("app/src/generated-sources/Api.java"))
    }

    @Test
    fun `test-skipping properties and parameters disable tests anywhere in a POM`() {
        for (config in listOf(
            "<skipTests>true</skipTests>",
            "<skipITs>true</skipITs>",
            "<maven.test.skip>true</maven.test.skip>",
            "<testFailureIgnore>true</testFailureIgnore>",
            "<maven.test.failure.ignore>true</maven.test.failure.ignore>",
        )) {
            val pom = "<project>\n  <properties>\n    $config\n  </properties>\n</project>\n"
            val name = config.substringAfter('<').substringBefore('>')
            assertEquals("`$name` skips tests or ignores their failures", adapter.testDisablingConfig("pom.xml", pom, 3), config)
        }
    }

    @Test
    fun `an exclude or skip in surefire or failsafe disables tests`() {
        val pom =
            """
            <plugin>
              <artifactId>maven-surefire-plugin</artifactId>
              <configuration>
                <excludes>
                  <exclude>**/SlowTest.java</exclude>
                </excludes>
              </configuration>
            </plugin>
            <plugin>
              <artifactId>maven-failsafe-plugin</artifactId>
              <configuration>
                <skip>true</skip>
              </configuration>
            </plugin>
            """.trimIndent()

        assertEquals("`<excludes>` in maven-surefire-plugin leaves tests out", adapter.testDisablingConfig("pom.xml", pom, 4))
        assertEquals("`<exclude>` in maven-surefire-plugin leaves tests out", adapter.testDisablingConfig("pom.xml", pom, 5))
        assertEquals("`<skip>` in maven-failsafe-plugin leaves tests out", adapter.testDisablingConfig("pom.xml", pom, 12))
    }

    @Test
    fun `an exclude elsewhere, and any other line, is fine`() {
        val pom =
            """
            <excludes><exclude>top</exclude></excludes>
            <plugin>
              <artifactId>jacoco-maven-plugin</artifactId>
              <configuration>
                <excludes><exclude>**/Generated*</exclude></excludes>
              </configuration>
            </plugin>
            <version>3.4.1</version>
            """.trimIndent()

        assertNull(adapter.testDisablingConfig("pom.xml", pom, 1), "no plugin around it")
        assertNull(adapter.testDisablingConfig("pom.xml", pom, 5), "not a test plugin")
        assertNull(adapter.testDisablingConfig("pom.xml", pom, 8))
    }
}
