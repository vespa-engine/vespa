// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.text.Text;

public record QuantizationParams(int bits) {

    public QuantizationParams {
        if (bits < 1 || bits > 4) {
            throw new IllegalArgumentException(Text.format("quantization bits must be a value in [1, 4], was %d", bits));
        }
    }

    public static QuantizationParams ofBits(int bits) {
        return new QuantizationParams(bits);
    }

}
