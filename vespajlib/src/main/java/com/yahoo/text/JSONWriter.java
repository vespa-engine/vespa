// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A class which knows how to write JSON markup. All methods return this to
 * enable chaining of method calls.
 * Consider using the Jackson generator API instead, as that may be faster.
 *
 * @author bratseth
 */
public final class JSONWriter {

    /** A stack maintaining the "needs comma" state at the current level */
    private final Deque<Boolean> needsComma = new ArrayDeque<>();

    private static final char[] DIGITS = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    private final OutputStream stream;

    public JSONWriter(OutputStream stream) {
        this.stream = stream;
    }

    /** Called on the start of a field or array value */
    private void beginFieldOrArrayValue() throws IOException {
        if (needsComma.getFirst()) {
            write(",");
        }
    }

    /** Called on the end of a field or array value */
    private void endFieldOrArrayValue() {
        setNeedsComma();
    }

    /** Begins an object field */
    public JSONWriter beginField(String fieldName) throws IOException {
        beginFieldOrArrayValue();
        write("\"" + fieldName + "\":");
        return this;
    }

    /** Ends an object field */
    public JSONWriter endField() throws IOException {
        endFieldOrArrayValue();
        return this;
    }

    /** Begins an array value */
    public JSONWriter beginArrayValue() throws IOException {
        beginFieldOrArrayValue();
        return this;
    }

    /** Ends an array value */
    public JSONWriter endArrayValue() throws IOException {
        endFieldOrArrayValue();
        return this;
    }

    /** Begin an object value */
    public JSONWriter beginObject() throws IOException {
        write("{");
        needsComma.addFirst(Boolean.FALSE);
        return this;
    }

    /** End an object value */
    public JSONWriter endObject() throws IOException {
        write("}");
        needsComma.removeFirst();
        return this;
    }

    /** Begin an array value */
    public JSONWriter beginArray() throws IOException {
        write("[");
        needsComma.addFirst(Boolean.FALSE);
        return this;
    }

    /** End an array value */
    public JSONWriter endArray() throws IOException {
        write("]");
        needsComma.removeFirst();
        return this;
    }

    /** Writes a string value */
    public JSONWriter value(String value) throws IOException {
        write("\"").write(escape(value)).write("\"");
        return this;
    }

    /** Writes a numeric value */
    public JSONWriter value(Number value) throws IOException {
        write(value.toString());
        return this;
    }

    /** Writes a boolean value */
    public JSONWriter value(boolean value) throws IOException {
        write(Boolean.toString(value));
        return this;
    }

    /** Writes a null value */
    public JSONWriter value() throws IOException {
        write("null");
        return this;
    }

    private void setNeedsComma() {
        if (level() == 0) return;
        needsComma.removeFirst();
        needsComma.addFirst(Boolean.TRUE);
    }

    /** Returns the current nested level */
    private int level() { return needsComma.size(); }

    /**
     * Writes a string directly as-is to the stream of this.
     *
     * @return this for convenience
     */
    private JSONWriter write(String string) throws IOException {
        if (string.length() == 0) return this;
        stream.write(Utf8.toBytes(string));
        return this;
    }

    /**
     * Do JSON escaping of a string.
     *
     * @param in a string to escape
     * @return a String suitable for use in JSON strings
     */
    private String escape(final String in) {
        final StringBuilder quoted = new StringBuilder((int) (in.length() * 1.2));
        return escape(in, quoted).toString();
    }

    /**
     * Do JSON escaping of the incoming string to the "quoted" buffer. The
     * buffer returned is the same as the one given in the "quoted" parameter.
     *
     * @param in a string to escape
     * @param escaped the target buffer for escaped data
     * @return the same buffer as given in the "quoted" parameter
     */
    private StringBuilder escape(final String in, final StringBuilder escaped) {
        for (final char c : in.toCharArray()) {
            switch (c) {
                case ('"'):
                    escaped.append("\\\"");
                    break;
                case ('\\'):
                    escaped.append("\\\\");
                    break;
                case ('\b'):
                    escaped.append("\\b");
                    break;
                case ('\f'):
                    escaped.append("\\f");
                    break;
                case ('\n'):
                    escaped.append("\\n");
                    break;
                case ('\r'):
                    escaped.append("\\r");
                    break;
                case ('\t'):
                    escaped.append("\\t");
                    break;
                default:
                    if (c < 32) {
                        escaped.append("\\u").append(fourDigitHexString(c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped;
    }

    private static char[] fourDigitHexString(final char c) {
        final char[] hex = new char[4];
        int in = ((c) & 0xFFFF);
        for (int i = 3; i >= 0; --i) {
            hex[i] = DIGITS[in & 0xF];
            in >>>= 4;
        }
        return hex;
    }

}
