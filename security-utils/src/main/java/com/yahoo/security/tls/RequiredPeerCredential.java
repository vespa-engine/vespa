// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class RequiredPeerCredential {

    public enum Field { CN, SAN_DNS, SAN_URI }

    private final Field field;
    private final Pattern pattern;

    private RequiredPeerCredential(Field field, Pattern pattern) {
        this.field = field;
        this.pattern = pattern;
    }

    public static RequiredPeerCredential of(Field field, String pattern) {
        return new RequiredPeerCredential(field, createPattern(field, pattern));
    }

    private static Pattern createPattern(Field field, String pattern) {
        switch (field) {
            case CN:
            case SAN_DNS:
                return new HostGlobPattern(pattern);
            case SAN_URI:
                return new UriGlobPattern(pattern);
            default:
                throw new IllegalArgumentException("Unknown field: " + field);
        }
    }

    public Field field() {
        return field;
    }

    public Pattern pattern() {
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

    public interface Pattern {
        String asString();
        boolean matches(String fieldValue);
    }
}
