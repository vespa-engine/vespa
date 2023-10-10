// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
class GlobPattern implements Comparable<GlobPattern> {

    private static final GlobPattern WILDCARD = new WildcardPattern();
    protected final String[] parts;

    private GlobPattern(String... parts) {
        this.parts = parts;
    }

    public final Match match(String text) {
        return match(text, 0);
    }

    public Match match(String text, int offset) {
        int[] pos = new int[parts.length - 1 << 1];
        if (!matches(text, offset, 0, pos)) {
            return null;
        }
        return new Match(text, pos);
    }

    private boolean matches(String text, int textIdx, int partIdx, int[] out) {
        String part = parts[partIdx];
        if (partIdx == parts.length - 1 && part.isEmpty()) {
            out[partIdx - 1 << 1 | 1] = text.length();
            return true; // optimize trailing wildcard
        }
        int partEnd = textIdx + part.length();
        if (partEnd > text.length()|| !text.startsWith(part, textIdx))  {
            return false;
        }
        if (partIdx == parts.length - 1) {
            return partEnd == text.length();
        }
        out[partIdx << 1] = partEnd;
        for (int i = partEnd; i <= text.length(); ++i) {
            out[partIdx << 1 | 1] = i;
            if (matches(text, i, partIdx + 1, out)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int compareTo(GlobPattern rhs) {
        // wildcard pattern always orders last
        if (parts.length == 0 || rhs.parts.length == 0) {
            return rhs.parts.length - parts.length;
        }
        // next is trailing wildcard
        int cmp = compare(parts[parts.length - 1], rhs.parts[rhs.parts.length - 1], false);
        if (cmp != 0) {
            return cmp;
        }
        // then comes part comparison
        for (int i = 0; i < parts.length && i < rhs.parts.length; ++i) {
            cmp = compare(parts[i], rhs.parts[i], true);
            if (cmp != 0) {
                return cmp;
            }
        }
        // one starts with the other, sort longest first
        return rhs.parts.length - parts.length;
    }

    private static int compare(String lhs, String rhs, boolean compareNonEmpty) {
        if ((lhs.isEmpty() || rhs.isEmpty()) && !lhs.equals(rhs)) {
            return rhs.length() - lhs.length();
        }
        if (!compareNonEmpty) {
            return 0;
        }
        return rhs.compareTo(lhs);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GlobPattern)) {
            return false;
        }
        GlobPattern rhs = (GlobPattern)obj;
        if (!Arrays.equals(parts, rhs.parts)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(parts);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < parts.length; ++i) {
            ret.append(parts[i]);
            if (i < parts.length - 1) {
                ret.append("*");
            }
        }
        return ret.toString();
    }

    public static Match match(String glob, String text) {
        return compile(glob).match(text);
    }

    public static GlobPattern compile(String pattern) {
        if (pattern.equals("*")) {
            return WILDCARD;
        }
        if (pattern.indexOf('*') < 0) {
            return new VerbatimPattern(pattern);
        }
        List<String> arr = new LinkedList<>();
        for (int prev = 0, next = 0; next <= pattern.length(); ++next) {
            if (next == pattern.length() || pattern.charAt(next) == '*') {
                arr.add(pattern.substring(prev, next));
                prev = next + 1;
            }
        }
        return new GlobPattern(arr.toArray(new String[arr.size()]));
    }

    public static class Match {

        private final String str;
        private final int[] pos;

        private Match(String str, int[] pos) {
            this.str = str;
            this.pos = pos;
        }

        public int groupCount() {
            return pos.length >> 1;
        }

        public String group(int idx) {
            return str.substring(pos[idx << 1], pos[idx << 1 | 1]);
        }
    }

    private static class VerbatimPattern extends GlobPattern {

        VerbatimPattern(String value) {
            super(value);
        }

        @Override
        public Match match(String text, int offset) {
            int len = text.length() - offset;
            if (len != parts[0].length()) {
                return null;
            }
            if (!parts[0].regionMatches(0, text, offset, len)) {
                return null;
            }
            return new Match(parts[0], new int[0]);
        }
    }

    private static class WildcardPattern extends GlobPattern {

        @Override
        public Match match(String text, int offset) {
            int len = text.length();
            if (len <= offset) {
                return new Match(text, new int[] { 0, 0 });
            }
            return new Match(text, new int[] { offset, len });
        }

        @Override
        public String toString() {
            return "*";
        }
    }
}
