package com.example.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CalculatorTest {
    @Test
    void adds() {
        assertEquals(3, new Calculator().add(1, 2));
    }

    @Test
    void addsWrong() {
        assertEquals(4, new Calculator().add(1, 2));
    }
}
