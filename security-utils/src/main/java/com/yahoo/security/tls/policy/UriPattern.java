// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.policy;

import java.util.Objects;

/**
 * Pattern used for matching URIs in X.509 certificate subject alternative names.
 *
 * @author bjorncs
 */
class UriPattern implements RequiredPeerCredential.Pattern {

    private final String pattern;

    UriPattern(String pattern) {
        this.pattern = pattern;
    }

    @Override public String asString() { return pattern; }

    @Override
    public boolean matches(String fieldValue) {
        // Only exact match is supported (unlike for host names)
        return fieldValue.equals(pattern);
    }

    @Override
    public String toString() {
        return "UriPattern{" +
                "pattern='" + pattern + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UriPattern that = (UriPattern) o;
        return Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern);
    }
}
