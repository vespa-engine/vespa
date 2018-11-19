// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.policy;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class HostGlobPattern {

    private final String pattern;

    public HostGlobPattern(String pattern) {
        this.pattern = pattern;
    }

    public String asString() {
        return pattern;
    }

    @Override
    public String toString() {
        return "HostGlobPattern{" +
                "pattern='" + pattern + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostGlobPattern that = (HostGlobPattern) o;
        return Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern);
    }
}
