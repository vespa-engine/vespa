// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Objects;

/**
 * Pattern used for matching URIs in X.509 certificate subject alternative names.
 *
 * @author bjorncs
 */
class UriGlobPattern implements RequiredPeerCredential.Pattern {

    private final GlobPattern globPattern;

    UriGlobPattern(String globPattern) {
        this.globPattern = new GlobPattern(globPattern, new char[] {'/'}, false);
    }

    @Override public String asString() { return globPattern.asString(); }

    @Override public boolean matches(String fieldValue) { return globPattern.matches(fieldValue); }

    @Override
    public String toString() {
        return "UriPattern{" +
                "pattern='" + globPattern + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UriGlobPattern that = (UriGlobPattern) o;
        return Objects.equals(globPattern, that.globPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(globPattern);
    }
}
