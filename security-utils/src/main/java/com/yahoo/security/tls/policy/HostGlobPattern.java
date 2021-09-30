// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.policy;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author bjorncs
 */
class HostGlobPattern implements RequiredPeerCredential.Pattern {

    private final String pattern;
    private final Pattern regexPattern;

    HostGlobPattern(String pattern) {
        this.pattern = pattern;
        this.regexPattern = toRegexPattern(pattern);
    }

    @Override
    public String asString() {
        return pattern;
    }

    @Override
    public boolean matches(String hostString) {
        return regexPattern.matcher(hostString).matches();
    }

    private static Pattern toRegexPattern(String pattern) {
        StringBuilder builder = new StringBuilder("^");
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                // Note: we explicitly stop matching at a dot separator boundary.
                // This is to make host name matching less vulnerable to dirty tricks.
                builder.append("[^.]*");
            } else if (c == '?') {
                // Same applies for single chars; they should only match _within_ a dot boundary.
                builder.append("[^.]");
            } else if (isRegexMetaCharacter(c)){
                builder.append("\\");
                builder.append(c);
            } else {
                builder.append(c);
            }
        }
        builder.append('$');
        return Pattern.compile(builder.toString());
    }

    private static boolean isRegexMetaCharacter(char c) {
        return "<([{\\^-=$!|]})?*+.>".indexOf(c) != -1; // note: includes '?' and '*'
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
