@file:Suppress("UNUSED")

package com.example

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// Each construct the guardrails look for. Only parsed, never compiled.
class OrderTest {
    @Test
    fun total() {
        val order = Order(2)
        assertEquals(4, order.total(), "total in €")
        assertThat(order.items())
            .hasSize(2)
    }

    @Disabled("flaky")
    @Test
    fun skipped() {
        assumeTrue(false)
    }

    @Test
    fun `expression body`() = assertEquals(4, Order(2).total())

    @Suppress("UNCHECKED_CAST")
    fun helper(): Any? {
        return null // TODO: real value
    }

    fun empty() {}

    fun later(): Int = TODO()

    // noinspection unused
    class Order(
        private val items: Int,
    ) {
        fun total() = items * 2
    }
}

object Orders {
    fun none(): Order? = null
}
