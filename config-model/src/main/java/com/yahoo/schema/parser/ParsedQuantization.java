// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.text.Text;

/**
 * Encapsulation of parsed properties relevant for quantized dense tensor attributes
 *
 * TODO record
 */
public class ParsedQuantization {

    private final int bits;

    private ParsedQuantization(int bits) {
        if (bits < 1 || bits > 4) {
            throw new IllegalArgumentException(Text.format("quantization bits must be a value in [1, 4], was %d", bits));
        }
        this.bits = bits;
    }

    public int bits() { return this.bits; }

    public static ParsedQuantization ofBits(int bits) {
        return new ParsedQuantization(bits);
    }

}
