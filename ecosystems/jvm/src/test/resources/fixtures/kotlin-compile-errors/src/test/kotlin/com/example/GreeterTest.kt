package com.example

import kotlin.test.Test
import kotlin.test.assertEquals

class GreeterTest {
    @Test
    fun greets() {
        assertEquals("Hello, Ada", Greeter().greet("Ada"))
    }
}
