// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.text.Text;

/**
 * Encapsulation of parsed properties relevant for quantized dense tensor attributes
 */
public record ParsedQuantization(int bits) {

    public ParsedQuantization {
        if (bits < 1 || bits > 4) {
            throw new IllegalArgumentException(Text.format("quantization bits must be a value in [1, 4], was %d", bits));
        }
    }

    public static ParsedQuantization ofBits(int bits) {
        return new ParsedQuantization(bits);
    }

}
