package gmbh.vanella.lector.ecosystems.jvm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The adapter's part of the scope and build-file guardrails. */
class MavenGuardrailsTest {
    private val adapter = MavenAdapter(plugins = MavenPlugins("rewrite", "help", "dependency")) { _, _, _, _ -> error("no Maven here") }

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
    fun `test-skipping options in the Maven config and the wrapper disable tests`() {
        assertEquals(
            "`skipTests` skips tests or ignores their failures",
            adapter.testDisablingConfig(".mvn/maven.config", "-B\n-DskipTests\n", 2),
        )
        assertEquals(
            "`maven.test.skip` skips tests or ignores their failures",
            adapter.testDisablingConfig("mvnw", "#!/bin/sh\nexec mvn -Dmaven.test.skip=true \"$@\"\n", 2),
        )
    }

    @Test
    fun `a line belongs to the plugin around it, whatever comes before it in the plugin`() {
        val pom =
            """
            <plugin>
              <artifactId>maven-surefire-plugin</artifactId>
              <dependencies>
                <dependency>
                  <artifactId>surefire-junit-platform</artifactId>
                </dependency>
              </dependencies>
              <configuration>
                <excludes><exclude>**/*</exclude></excludes>
              </configuration>
            </plugin>
            <plugin>
              <configuration>
                <skip>true</skip>
              </configuration>
              <artifactId>maven-failsafe-plugin</artifactId>
            </plugin>
            """.trimIndent()

        assertEquals("`<excludes>` in maven-surefire-plugin leaves tests out", adapter.testDisablingConfig("pom.xml", pom, 9))
        assertEquals("`<skip>` in maven-failsafe-plugin leaves tests out", adapter.testDisablingConfig("pom.xml", pom, 14))
    }

    @Test
    fun `includes, a test filter, groups, and every skip in surefire or failsafe leave tests out`() {
        for (config in listOf(
            "<includes><include>None</include></includes>",
            "<test>None</test>",
            "<groups>slow</groups>",
            "<skipExec>true</skipExec>",
            "<skipAfterFailureCount>1</skipAfterFailureCount>",
        )) {
            val pom = "<plugin>\n  <artifactId>maven-surefire-plugin</artifactId>\n  $config\n</plugin>\n"
            val element = config.substringBefore('>') + ">"
            assertEquals("`$element` in maven-surefire-plugin leaves tests out", adapter.testDisablingConfig("pom.xml", pom, 3), config)
        }
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
        assertNull(
            adapter.testDisablingConfig("pom.xml", "<plugin>\n  <artifactId>maven-surefire-plugin</artifactId>\n  <skip>true</skip>\n", 3),
            "a plugin that never closes is no plugin; Maven rejects the POM anyway",
        )
    }
}
