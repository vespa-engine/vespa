// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.Objects;

/**
 * An substring which also provides access to the full (query) string it is a substring of.
 * This is a value object.
 *
 * @author bratseth
 */
public class Substring {

    /** The start of the substring */
    public final int start;

    /** The end of the substring */
    public final int end;

    /** The string this is a substring of */
    public final String string;

    /** Creates a substring which is identical to the string containing it */
    public Substring(String string) {
        this.start = 0;
        this.end = string.length();
        this.string=string;
    }

    public Substring(int start, int end, String string) {
        this.start = start;
        this.end = end;
        this.string=string;
    }

    public String getValue() {
        return string.substring(start, end);
    }

    /** Returns the entire string this is a substring of. The start and end offsets are into this string. */
    public String getSuperstring() { return string; }

    /**
     * Returns the character n places (0 base) after the end of the value substring into the superstring.
     * For example charAfter(0) returns the first character after the end of the substring
     *
     * @return the char n planes after the end of the substring
     * @throws IndexOutOfBoundsException if the string is not long enough to have a character at this position
     */
    public char charAfter(int n) {
        return string.charAt(end+n);
    }

    /**
     * Returns the character n places (0 base) before the start of the value substring into the superstring.
     * For example charBefore(0) returns the first character before the start of the substring
     *
     * @return the char n planes before the start of the substring
     * @throws IndexOutOfBoundsException if the string does not have a character at this position
     */
    public char charBefore(int n) {
        return string.charAt(start-1-n);
    }

    @Override
    public String toString() {
        return "(" + start + ' ' + end + ')';
    }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Substring)) return false;
        var other = (Substring)o;
        if (this.start != other.start) return false;
        if (this.end != other.end) return false;
        if (! Objects.equals(this.string, other.string)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end, string);
    }

}
