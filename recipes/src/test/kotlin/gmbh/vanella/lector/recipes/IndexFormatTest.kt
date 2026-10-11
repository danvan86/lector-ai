package gmbh.vanella.lector.recipes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IndexFormatTest {
    private val entry =
        IndexEntry(
            ecosystem = "maven",
            packagePattern = "org.springframework.boot:*",
            selector = "3.4.x",
            recipe = "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4",
            depth = 1,
            artifact = "org.openrewrite.recipe:rewrite-spring:6.37.0",
            license = "Moderne Source Available License",
        )

    @Test
    fun `entries survive a round trip`() {
        assertEquals(listOf(entry), IndexFormat.read(IndexFormat.write(listOf(entry))))
    }

    @Test
    fun `field names are generic, with no build-tool terms`() {
        val json = Json.parseToJsonElement(IndexFormat.write(listOf(entry)))
        val fields =
            json.jsonObject["entries"]!!
                .jsonArray
                .single()
                .jsonObject.keys

        assertEquals(setOf("ecosystem", "package", "selector", "recipe", "depth", "artifact", "license"), fields)
    }

    @Test
    fun `malformed json is an error that says what is wrong`() {
        val error = assertFailsWith<IllegalArgumentException> { IndexFormat.read("""{"entries": [{"ecosystem": "maven"}]}""") }

        assertContains(error.message.orEmpty(), "Malformed recipe index")
    }

    @Test
    fun `text that isn't json is malformed`() {
        val error = assertFailsWith<IllegalArgumentException> { IndexFormat.read("not json") }

        assertContains(error.message.orEmpty(), "Malformed recipe index")
    }

    @Test
    fun `json that isn't an object is malformed`() {
        val error = assertFailsWith<IllegalArgumentException> { IndexFormat.read("[]") }

        assertContains(error.message.orEmpty(), "Malformed recipe index")
    }

    @Test
    fun `the bundled index is read from the classpath`() {
        val index = BundledRecipeIndex.load("test-recipe-index.json")

        assertEquals(listOf(entry), index.entries)
    }

    @Test
    fun `a missing index resource is an error that names it`() {
        val error = assertFailsWith<IllegalStateException> { BundledRecipeIndex.load("no-such-index.json") }

        assertEquals("Recipe index no-such-index.json is missing from the classpath", error.message)
    }
}
