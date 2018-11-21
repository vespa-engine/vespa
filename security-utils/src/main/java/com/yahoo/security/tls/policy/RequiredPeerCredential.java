// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.policy;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class RequiredPeerCredential {

    public enum Field { CN, SAN_DNS }

    private final Field field;
    private final HostGlobPattern pattern;

    public RequiredPeerCredential(Field field, HostGlobPattern pattern) {
        this.field = field;
        this.pattern = pattern;
    }

    public Field field() {
        return field;
    }

    public HostGlobPattern pattern() {
        return pattern;
    }

    @Override
    public String toString() {
        return "RequiredPeerCredential{" +
                "field=" + field +
                ", pattern=" + pattern +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequiredPeerCredential that = (RequiredPeerCredential) o;
        return field == that.field &&
                Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, pattern);
    }
}
