package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReleasesTest {
    @Test
    void knowsReleasesUpToJava17() {
        assertTrue(Releases.isKnown("JAVA_17"));
        assertFalse(Releases.isKnown("JAVA_21"));
    }

    @Test
    void addsWrong() {
        assertEquals(5, Releases.add(2, 2));
    }

    @Test
    void adds() {
        assertEquals(4, Releases.add(2, 2));
    }
}
