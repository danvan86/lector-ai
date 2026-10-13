package com.example

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GreeterTest {
    @Test
    fun greets() {
        assertEquals("Hello, Ada", Greeter().greet("ada"))
    }
}
