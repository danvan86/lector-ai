package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreetingTest {
    @Test
    void greetsByName() {
        assertEquals("Hello, Ada!", new Greeting("Ada").text());
    }

    @Test
    void greetsTheWorldWithoutAName() {
        assertEquals("Hello, World!", new Greeting(null).text());
    }
}
