package com.example.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CalculatorTest {
    private final Calculator calculator = new Calculator();

    @Test
    void adds() {
        assertEquals(4, calculator.add(2, 2));
    }

    @Test
    void addsWrong() {
        assertEquals(5, calculator.add(2, 2));
    }

    @Test
    void dividesByZero() {
        assertEquals(0, calculator.divide(1, 0));
    }
}
