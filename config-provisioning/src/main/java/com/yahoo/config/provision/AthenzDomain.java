// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.regex.Pattern;

/**
 * @author mortent
 */
public class AthenzDomain {

    private static final Pattern PATTERN = Pattern.compile("[a-zA-Z0-9_][a-zA-Z0-9_\\-.]*[a-zA-Z0-9_]");

    private final String name;

    private AthenzDomain(String name) {
        // TODO bjorncs: Temporarily disable name validation
        // validateName(name);
        this.name = name;
    }

    private static void validateName(String name) {
        if (!PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Not a valid domain name: '" + name + "'");
        }
    }

    public static AthenzDomain from(String value) {
        return new AthenzDomain(value);
    }

    public String value() { return name; }

    @Override
    public String toString() {
        return "AthenzDomain{" +
               "name='" + name + '\'' +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AthenzDomain that = (AthenzDomain) o;

        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
