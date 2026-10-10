package com.example.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreeterTest {
    @Test
    void greets() {
        assertEquals("Hello, Ada", new Greeter().greetPolitely("Ada"));
    }
}
