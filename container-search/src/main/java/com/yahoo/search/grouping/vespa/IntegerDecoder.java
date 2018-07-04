// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

/**
 * @author Simon Thoresen Hult
 */
class IntegerDecoder {

    private static final int CHAR_MIN = IntegerEncoder.CHARS[0];
    private static final int CHAR_MAX = IntegerEncoder.CHARS[IntegerEncoder.CHARS.length - 1];
    private final String input;
    private int pos = 0;

    public IntegerDecoder(String input) {
        this.input = input;
    }

    public boolean hasNext() {
        return pos < input.length();
    }

    public int next() {
        int val = 0;
        int len = decodeChar(input.charAt(pos++));
        for (int i = 0; i < len; i++) {
            val = (val << 4) | decodeChar(input.charAt(pos + i));
        }
        pos += len;
        return (val >>> 1) ^ (-(val & 0x1));
    }

    private static int decodeChar(char c) {
        if (c >= CHAR_MIN && c <= CHAR_MAX) {
            return (0xF & (c - CHAR_MIN));
        } else {
            throw new NumberFormatException(String.valueOf(c));
        }
    }
}
