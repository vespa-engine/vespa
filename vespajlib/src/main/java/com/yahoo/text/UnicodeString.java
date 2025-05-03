// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.util.Objects;

/**
 * A string wrapper with some convenience methods for dealing with UTF-16 surrogate pairs (a crime against humanity).
 *
 * @author bratseth
 */
public class UnicodeString {

    private final String s;

    public UnicodeString(String s) {
        this.s = Objects.requireNonNull(s);
    }

    /** Substring in code point space */
    public UnicodeString substring(int start, int codePoints) {
        int cps = codePoints * 2 <= s.length() - start ? codePoints
                          : Math.min(codePoints, s.codePointCount(start, s.length()));
        return new UnicodeString(s.substring(start, s.offsetByCodePoints(start, cps)));
    }

    /** Returns the position count code points after start (which may be past the end of the string) */
    public int skip(int codePointCount, int start) {
        int index = start;
        for (int i = 0; i < codePointCount; i++) {
            index = nextIndex(index);
            if (index > s.length()) break;
        }
        return index;
    }

    /** Returns the index of the next code point after the given index (which may be past the end of the string) */
    public int nextIndex(int index) {
        int next = index + 1;
        if (next < s.length() && Character.isLowSurrogate(s.charAt(next)))
            next++;
        return next;
    }

    /**
     * Returns the code point at the next position after the given index,
     * or \u0000 if the index is at the last code point
     */
    public int nextCodePoint(int index) {
        int next = nextIndex(index);
        return next >= s.length() ? '\u0000' : s.codePointAt(next);
    }

    /** Returns the number of positions (not code points) in this */
    public int length() { return s.length(); }

    /** Returns the number of code points in this */
    public int codePointCount() { return s.codePointCount(0, s.length()); }

    public int codePointAt(int index) {
        return s.codePointAt(index);
    }

    @Override
    public String toString() { return s; }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof UnicodeString other)) return false;
        return this.s.equals(other.s);
    }

    @Override
    public int hashCode() {
        return s.hashCode();
    }

}
