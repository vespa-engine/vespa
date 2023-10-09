// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import java.util.Objects;
import java.util.Set;

public class Validation {
    static final Set<String> validDiskSpeeds = Set.of("slow", "fast", "any");
    static final Set<String> validStorageTypes = Set.of("remote", "local", "any");
    static final Set<String> validClusterTypes = Set.of("container", "content", "combined", "admin");
    static final Set<String> validArchitectures = Set.of("arm64", "x86_64", "any");

    static double requirePositive(String name, Double value) {
        requireNonNull(name, value);
        if (value <= 0)
            throw new IllegalArgumentException("'" + name + "' must be positive, was " + value);
        return value;
    }

    static int requirePositive(String name, Integer value) {
        requireNonNull(name, value);
        if (value <= 0)
            throw new IllegalArgumentException("'" + name + "' must be positive, was " + value);
        return value;
    }

    static double requireNonNegative(String name, double value) {
        if (value < 0)
            throw new IllegalArgumentException("'" + name + "' must be positive, was " + value);
        return value;
    }

    static String validateEnum(String name, Set<String> validValues, String value) {
        requireNonNull(name, value);
        if (!validValues.contains(value))
            throw new IllegalArgumentException("Invalid " + name + ", valid values are: " +
                                                       validValues + ", got: " + value);
        return value;
    }

    private static <T> T requireNonNull(String name, T value) {
        return Objects.requireNonNull(value, () -> "'" + name + "' has not been specified");
    }

}
