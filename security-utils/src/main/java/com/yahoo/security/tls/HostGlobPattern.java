// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Objects;

/**
 * @author bjorncs
 */
class HostGlobPattern implements RequiredPeerCredential.Pattern {

    private final GlobPattern globPattern;

    HostGlobPattern(String pattern) {
        this.globPattern = new GlobPattern(pattern, new char[] {'.'}, true);
    }

    @Override
    public String asString() {
        return globPattern.asString();
    }

    @Override
    public boolean matches(String hostString) {
        return globPattern.matches(hostString);
    }

    @Override
    public String toString() {
        return "HostGlobPattern{" +
                "pattern='" + globPattern + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostGlobPattern that = (HostGlobPattern) o;
        return Objects.equals(globPattern, that.globPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(globPattern);
    }
}
