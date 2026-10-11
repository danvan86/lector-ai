package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// Each construct the guardrails look for. Only parsed, never compiled.
class OrderTest {
    @Test
    void total() {
        Order order = new Order(2);
        assertEquals(4, order.total(), "total in €");
        assertThat(order.items())
            .hasSize(2);
    }

    @Disabled("flaky")
    @org.junit.jupiter.api.Test
    void skipped() {
        assumeTrue(false);
        assert order != null;
    }

    @SuppressWarnings("unchecked")
    Object helper() {
        return null; // TODO: real value
    }

    void empty() { }

    //noinspection unused
    static class Order {
        Order(int items) {
        }

        int total() {
            return 4;
        }
    }
}
