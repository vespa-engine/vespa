// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.text;

import java.util.Objects;

/**
 * Cursor is a mutable offset into a fixed String, and useful for String parsing.
 *
 * @author hakonhall
 */
// @Mutable
public class Cursor {
    private final String text;
    private int offset;
    private TextLocation locationCache;

    /** Creates a pointer to the first char of {@code text}, which is EOT if {@code text} is empty. */
    public Cursor(String text) { this(text, 0, new TextLocation()); }

    public Cursor(Cursor that) { this(that.text, that.offset, that.locationCache); }

    private Cursor(String text, int offset, TextLocation location) {
        this.text = Objects.requireNonNull(text);
        this.offset = offset;
        this.locationCache = Objects.requireNonNull(location);
    }

    /** Returns the substring of {@code text} starting at {@link #offset()} (to EOT). */
    @Override
    public String toString() { return text.substring(offset); }

    public String fullText() { return text; }
    public int offset() { return offset; }
    public boolean bot() { return offset == 0; }
    public boolean eot() { return offset == text.length(); }
    public boolean startsWith(char c) { return offset < text.length() && text.charAt(offset) == c; }
    public boolean startsWith(String prefix) { return text.startsWith(prefix, offset); }

    /** @throws IndexOutOfBoundsException if {@link #eot()}. */
    public char getChar() { return text.charAt(offset); }

    /** The number of chars between pointer and EOT. */
    public int length() { return text.length() - offset; }

    /** Calculate the current text location in O(length(text)). */
    public TextLocation calculateLocation() {
        if (offset < locationCache.offset()) {
            locationCache = new TextLocation();
        } else if (offset == locationCache.offset()) {
            return locationCache;
        }

        int lineIndex = locationCache.lineIndex();
        int columnIndex = locationCache.columnIndex();
        for (int i = locationCache.offset(); i < offset; ++i) {
            if (text.charAt(i) == '\n') {
                ++lineIndex;
                columnIndex = 0;
            } else {
                ++columnIndex;
            }
        }

        locationCache = new TextLocation(offset, lineIndex, columnIndex);
        return locationCache;
    }

    public void set(Cursor that) {
        if (that.text != text) {
            throw new IllegalArgumentException("'that' doesn't refer to the same text");
        }

        this.offset = that.offset;
    }

    /** Advance substring.length() if this startsWith the substring, returning true if so. */
    public boolean skip(String substring) {
        if (startsWith(substring)) {
            offset += substring.length();
            return true;
        } else {
            return false;
        }
    }

    public boolean skip(char c) {
        if (startsWith(c)) {
            ++offset;
            return true;
        } else {
            return false;
        }
    }

    /** If the current char is a whitespace, skip it and return true. */
    public boolean skipWhitespace() {
        if (!eot() && Character.isWhitespace(getChar())) {
            ++offset;
            return true;
        } else {
            return false;
        }
    }

    /** Returns true if at least one whitespace was skipped. */
    public boolean skipWhitespaces() {
        if (skipWhitespace()) {
            while (skipWhitespace())
                ++offset;
            return true;
        } else {
            return false;
        }
    }

    /** Return false if eot(), otherwise advance to the next char and return true. */
    public boolean increment() {
        if (eot()) return false;
        ++offset;
        return true;
    }

    /**
     * Advance {@code distance} chars until bot() or eot() is reached (distance may be negative),
     * and return true if this cursor moved the full distance.
     */
    public boolean advance(int distance) {
        int newOffset = offset + distance;
        if (newOffset < 0) {
            this.offset = 0;
            return false;
        } else if (newOffset > text.length()) {
            this.offset = text.length();
            return false;
        } else {
            this.offset = newOffset;
            return true;
        }
    }

    /** Advance pointer until start of needle is found (and return true), or EOT is reached (and return false). */
    public boolean advanceTo(String needle) {
        int index = text.indexOf(needle, offset);
        if (index == -1) {
            offset = text.length();
            return false; // and eot() is true
        } else {
            offset = index;
            return true; // and eot() is false
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cursor cursor = (Cursor) o;
        return offset == cursor.offset && text.equals(cursor.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, offset);
    }
}
