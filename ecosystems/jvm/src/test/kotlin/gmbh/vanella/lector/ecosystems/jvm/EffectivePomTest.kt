package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.ToolError
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EffectivePomTest {
    @Test
    fun `reads the parent, the dependencies with their managed versions, and the plugins`() {
        val versions = EffectivePom.versions(resource("effective-pom-spring-boot.xml"))

        assertEquals(
            mapOf(
                "org.springframework.boot:spring-boot-starter-parent" to "3.4.1",
                "org.springframework.boot:spring-boot" to "3.4.1",
                "org.springframework:spring-web" to "6.2.1",
                "org.junit.jupiter:junit-jupiter" to "5.11.4",
                "org.apache.maven.plugins:maven-clean-plugin" to "3.4.0",
                "org.apache.maven.plugins:maven-resources-plugin" to "3.3.1",
                "org.apache.maven.plugins:maven-jar-plugin" to "3.4.2",
                "org.apache.maven.plugins:maven-compiler-plugin" to "3.13.0",
                "org.apache.maven.plugins:maven-surefire-plugin" to "3.5.2",
                "org.apache.maven.plugins:maven-install-plugin" to "3.1.3",
                "org.apache.maven.plugins:maven-deploy-plugin" to "3.1.3",
                "org.apache.maven.plugins:maven-site-plugin" to "3.12.1",
            ),
            versions,
        )
    }

    @Test
    fun `managed versions that no module uses are not packages of the project`() {
        val versions = EffectivePom.versions(resource("effective-pom-spring-boot.xml"))

        assertEquals(null, versions["org.apache.activemq:activemq-console"])
        assertEquals(null, versions["org.codehaus.mojo:build-helper-maven-plugin"])
    }

    @Test
    fun `reads every module of a multi-module build, but not the modules themselves`() {
        val versions = EffectivePom.versions(resource("effective-pom-multi-module.xml"))

        assertEquals("5.13.4", versions["org.junit.jupiter:junit-jupiter"])
        assertEquals("3.5.5", versions["org.apache.maven.plugins:maven-failsafe-plugin"])
        assertEquals("3.4.1", versions["org.apache.maven.plugins:maven-jar-plugin"])
        assertEquals(null, versions["com.example:failing-tests"])
        assertEquals(null, versions["com.example:lib"])
    }

    @Test
    fun `a package with different versions in different modules resolves to the highest`() {
        val xml =
            projects(
                project("a", dependency("org.example", "lib", "1.10.0")),
                project("b", dependency("org.example", "lib", "1.9.0")),
            )

        assertEquals(mapOf("org.example:lib" to "1.10.0"), EffectivePom.versions(xml))
    }

    @Test
    fun `entries without a version are left out`() {
        val xml =
            projects(
                project(
                    "a",
                    "<dependencies><dependency><groupId>org.example</groupId><artifactId>lib</artifactId></dependency></dependencies>",
                ),
            )

        assertEquals(emptyMap(), EffectivePom.versions(xml))
    }

    @Test
    fun `malformed output is a tool error`() {
        val error = assertFailsWith<ToolError> { EffectivePom.versions("<projects><project>") }

        assertContains(error.message, "Can't read Maven's effective POM")
    }

    @Test
    fun `a document type declaration is rejected, not resolved`() {
        val xml = """<?xml version="1.0"?><!DOCTYPE project [<!ENTITY x SYSTEM "file:///etc/passwd">]><project/>"""

        val error = assertFailsWith<ToolError> { EffectivePom.versions(xml) }

        assertContains(error.message, "Can't read Maven's effective POM")
    }

    private fun resource(name: String) = requireNotNull(javaClass.getResource("/maven-output/$name")) { "$name is missing" }.readText()

    private fun projects(vararg projects: String) = "<projects>${projects.joinToString("")}</projects>"

    private fun project(
        artifactId: String,
        body: String,
    ) =
        """<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>com.example</groupId><artifactId>$artifactId</artifactId>$body</project>"""

    private fun dependency(
        groupId: String,
        artifactId: String,
        version: String,
    ) =
        "<dependencies><dependency><groupId>$groupId</groupId><artifactId>$artifactId</artifactId><version>$version</version></dependency></dependencies>"
}
