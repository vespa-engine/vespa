// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * Represents a rotation name for a container cluster. Typically created from the rotation element in services.xml.
 *
 * @author mpolden
 */
public class RotationName implements Comparable<RotationName> {

    private final String name;

    private RotationName(String name) {
        this.name = requireNonBlank(name, "name must be non-empty");
    }

    public String value() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RotationName that = (RotationName) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public int compareTo(RotationName o) {
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return "rotation '" + name + "'";
    }

    public static RotationName from(String name) {
        return new RotationName(name);
    }

    private static String requireNonBlank(String s, String message) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return s;
    }

}
