package com.example;

import org.apache.commons.lang4.StringUtils;
import org.apache.commons.lang3.Validate;

public class App {
    public static String run(String name) {
        Validate.notBlank(name);
        return StringUtils.capitalize(name);
    }
}
