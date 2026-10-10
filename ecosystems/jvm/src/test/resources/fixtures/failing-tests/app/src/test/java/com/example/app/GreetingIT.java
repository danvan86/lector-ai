package com.example.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreetingIT {
    @Test
    void greets() {
        assertEquals("Hello, world", new Greeting().text());
    }
}
