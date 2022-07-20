// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Matching engine for glob patterns having where one ore more alternative characters acts a boundary for wildcard matching.
 *
 * @author bjorncs
 */
class GlobPattern {
    private final String pattern;
    private final char[] boundaries;
    private final Pattern regexPattern;

    GlobPattern(String pattern, char[] boundaries, boolean enableSingleCharWildcard) {
        this.pattern = pattern;
        this.boundaries = boundaries;
        this.regexPattern = toRegexPattern(pattern, boundaries, enableSingleCharWildcard);
    }

    boolean matches(String value) { return regexPattern.matcher(value).matches(); }

    String asString() { return pattern; }
    Pattern regexPattern() { return regexPattern; }
    char[] boundaries() { return boundaries; }

    private static Pattern toRegexPattern(String pattern, char[] boundaries, boolean enableSingleCharWildcard) {
        StringBuilder builder = new StringBuilder("^");
        StringBuilder precedingCharactersToQuote = new StringBuilder();
        char[] chars = pattern.toCharArray();
        for (char c : chars) {
            if ((enableSingleCharWildcard && c == '?') || c == '*') {
                builder.append(quotePrecedingLiteralsAndReset(precedingCharactersToQuote));
                // Note: we explicitly stop matching at a separator boundary.
                // This is to make matching less vulnerable to dirty tricks (e.g dot as boundary for hostnames).
                // Same applies for single chars; they should only match _within_ a boundary.
                builder.append("[^").append(Pattern.quote(new String(boundaries))).append("]");
                if (c == '*') builder.append('*');
            } else {
                precedingCharactersToQuote.append(c);
            }
        }
        return Pattern.compile(builder.append(quotePrecedingLiteralsAndReset(precedingCharactersToQuote)).append('$').toString());
    }

    // Combines multiple subsequent literals inside a single quote to simplify produced regex patterns
    private static String quotePrecedingLiteralsAndReset(StringBuilder literals) {
        if (literals.length() > 0) {
            String quoted = literals.toString();
            literals.setLength(0);
            return Pattern.quote(quoted);
        }
        return "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GlobPattern that = (GlobPattern) o;
        return Objects.equals(pattern, that.pattern) && Arrays.equals(boundaries, that.boundaries);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(pattern);
        result = 31 * result + Arrays.hashCode(boundaries);
        return result;
    }

    @Override
    public String toString() {
        return "GlobPattern{" +
                "pattern='" + pattern + '\'' +
                ", boundaries=" + Arrays.toString(boundaries) +
                ", regexPattern=" + regexPattern +
                '}';
    }
}
