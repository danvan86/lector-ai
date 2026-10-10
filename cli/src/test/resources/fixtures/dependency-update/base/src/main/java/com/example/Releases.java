package com.example;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.JavaVersion;

public class Releases {
    /** Whether commons-lang knows the Java release, for example "JAVA_17". */
    public static boolean isKnown(String release) {
        return EnumUtils.isValidEnum(JavaVersion.class, release);
    }

    public static int add(int a, int b) {
        return a + b;
    }
}
