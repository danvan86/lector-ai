package com.example.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreetingIT {
    @Test
    void greets() {
        assertEquals("1 + 1 = 2", App.greeting());
    }

    @Test
    void greetsWrong() {
        assertEquals("1 + 1 = 3", App.greeting());
    }
}
