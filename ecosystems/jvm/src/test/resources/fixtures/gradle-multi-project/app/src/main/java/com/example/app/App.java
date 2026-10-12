package com.example.app;

import com.example.lib.Calculator;

public class App {
    public static String greeting() {
        return "1 + 1 = " + new Calculator().add(1, 1);
    }

    public static void main(String[] args) {
        System.out.println(greeting());
    }
}
