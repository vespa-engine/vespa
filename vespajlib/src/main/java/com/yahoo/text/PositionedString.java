// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * A string which has a current position.
 * Useful for writing simple single-pass parsers.
 *
 * @author bratseth
 */
public class PositionedString {

    private final String s;
    private int p;

    /**
     * Creates this from a given string.
     */
    public PositionedString(String string) {
        this.s=string;
    }

    /** The complete string value of this */
    public String string() { return s; }

    /** The current position into this string */
    public int position() { return p; }

    /** Assigns the current position in the string  */
    public void setPosition(int position) { p=position; }

    /**
     * Consumes the character at this position.
     * <br>Precondition: The character at this position is c.
     * <br>Postcondition: The position is increased by 1
     *
     * @param c the expected character at this
     * @throws IllegalArgumentException if the character at this position is not c
     */
    public void consume(char c) {
        if (s.charAt(p++)!=c)
            throw new IllegalArgumentException("Expected '" + c + "' " + at(p -1));
    }

    /**
     * Consumes zero or more whitespace characters starting at the current position
     */
    public void consumeSpaces() {
        while (Character.isWhitespace(s.charAt(p)))
            p++;
    }

    /**
     * Advances the position by 1 if the character at the current position is c.
     * Does nothing otherwise.
     *
     * @return whether this consumed a c at the current position, or if it did nothing
     */
    public boolean consumeOptional(char c) {
        if (s.charAt(p)!=c) return false;
        p++;
        return true;
    }

    /**
     * Returns whether the character at the current position is c.
     */
    public boolean peek(char c) {
        return s.charAt(p)==c;
    }

    /**
     * Returns the position of the next occurrence of c,
     * or -1 if there are no occurrences of c in the string after the current position.
     */
    public int indexOf(char c) {
        return s.indexOf(c,p);
    }

    /** Adds n to the current position */
    public void skip(int n) {
        p = p +n;
    }

    /**
     * Sets the position of this to the next occurrence of c after the current position.
     *
     * @param c the char to move the position to
     * @return the substring between the current position and the new position at c
     * @throws IllegalArgumentException if there was no occurrence of c after the current position
     */
    public String consumeTo(char c) {
        int nextC=indexOf(c);
        if (nextC<0)
            throw new IllegalArgumentException("Expected a string terminated by '" + c + "' " + at());
        String value=substring(nextC);
        p=nextC;
        return  value;
    }

    /**
     * Returns the substring between the current position and <code>position</code>
     * and advances the current position to <code>position</code>
     */
    public String consumeToPosition(int position) {
        String consumed=substring(position);
        p=position;
        return consumed;
    }

    /** Returns a substring of this from the current position to the end argument */
    public String substring(int end) {
        return string().substring(position(),end);
    }

    /** Returns the substring of this string from the current position to the end */
    public String substring() {
        return string().substring(position());
    }

    /** Returns a textual description of the current position, useful for appending to error messages. */
    public String at() {
        return at(p);
    }

    /** Returns a textual description of a given position, useful for appending to error messages. */
    public String at(int position) {
        return "starting at position " + position + " but was '" + s.charAt(position) + "'";
    }

    /** Returns the string */
    @Override
    public String toString() {
        return s;
    }

}
