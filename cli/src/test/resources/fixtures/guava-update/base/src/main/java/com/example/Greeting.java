package com.example;

import com.google.common.base.Objects;

public class Greeting {
    private final String name;

    public Greeting(String name) {
        this.name = name;
    }

    /** The name, or "World" if there is none. */
    public String name() {
        return Objects.firstNonNull(name, "World");
    }

    public String text() {
        return "Hello, " + name() + "!";
    }
}
