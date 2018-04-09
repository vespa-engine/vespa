// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

/**
 * Simple encoding scheme to remove space, linefeed, control characters and
 * anything outside ISO 646.irv:1991 from strings. The scheme is supposed to be
 * human readable and debugging friendly. Opening and closing curly braces are
 * used as quoting characters, the output is by definition US-ASCII only
 * characters.
 *
 * @author Steinar Knutsen
 */
public final class Encoder {

    /**
     * ISO 646.irv:1991 safe quoting into a StringBuilder instance.
     *
     * @param input
     *            the string to encode
     * @param output
     *            the destination buffer
     * @return the destination buffer given as input
     */
    public static StringBuilder encode(String input, StringBuilder output) {
        for (int i = 0; i < input.length(); i = input.offsetByCodePoints(i, 1)) {
            int c = input.codePointAt(i);
            if (c <= '~') {
                if (c <= ' ') {
                    encode(c, output);
                } else {
                    switch (c) {
                    case '{':
                    case '}':
                        encode(c, output);
                        break;
                    default:
                        output.append((char) c);
                    }
                }
            } else {
                encode(c, output);
            }
        }
        return output;
    }

    /**
     * ISO 646.irv:1991 safe unquoting into a StringBuilder instance.
     *
     * @param input
     *            the string to decode
     * @param output
     *            the destination buffer
     * @return the destination buffer given as input
     * @throws IllegalArgumentException
     *             if the input string contains unexpected or invalid data
     */
    public static StringBuilder decode(String input, StringBuilder output) {
        for (int i = 0; i < input.length(); i = input.offsetByCodePoints(i, 1)) {
            int c = input.codePointAt(i);
            if (c > '~') {
                throw new IllegalArgumentException("Input contained character above printable ASCII.");
            }
            switch (c) {
                case '{':
                    i = decode(input, i, output);
                    break;
                default:
                    output.append((char) c);
                    break;
            }
        }
        return output;
    }

    private static int decode(String input, int offset, StringBuilder output) {
        char c = 0;
        int end = offset;
        int start = offset + 1;
        int codePoint;

        while ('}' != c) {
            if (++end >= input.length()) {
                throw new IllegalArgumentException("Unterminated quoted character or empty quoting.");
            }
            c = input.charAt(end);
        }
        try {
            codePoint = Integer.parseInt(input.substring(start, end), 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unexpected quoted data: [" + input.substring(start, end) + "]", e);
        }
        if (Character.charCount(codePoint) > 1) {
            try {
                output.append(Character.toChars(codePoint));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unexpected quoted data: [" + input.substring(start, end) + "]", e);
            }
        } else {
            output.append((char) codePoint);
        }
        return end;

    }

    private static void encode(int c, StringBuilder output) {
        output.append("{").append(Integer.toHexString(c)).append("}");
    }

}
