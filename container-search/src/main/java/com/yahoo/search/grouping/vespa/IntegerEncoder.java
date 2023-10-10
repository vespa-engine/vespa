// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

/**
 * @author Simon Thoresen Hult
 */
class IntegerEncoder {

    public static final char[] CHARS = { 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
                                         'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P' };
    private final StringBuilder out = new StringBuilder();

    public void append(int val) {
        val = ((val << 1) ^ (val >> 31));
        int cnt = 8;
        for (int i = 0; i < 8; ++i) {
            if (((val >> (28 - 4 * i)) & 0xF) != 0) {
                break;
            }
            --cnt;
        }
        out.append(CHARS[cnt]);
        for (int i = 8 - cnt; i < 8; ++i) {
            out.append(CHARS[(val >> (28 - 4 * i)) & 0xF]);
        }
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
