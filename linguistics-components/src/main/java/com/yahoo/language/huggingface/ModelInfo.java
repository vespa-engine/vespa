// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.language.huggingface;

import java.util.Arrays;

/**
 * @author bjorncs
 */
public record ModelInfo(
        TruncationStrategy truncation, PaddingStrategy padding, int maxLength, int stride, int padToMultipleOf) {

    public enum TruncationStrategy {
        LONGEST_FIRST,
        ONLY_FIRST,
        ONLY_SECOND,
        DO_NOT_TRUNCATE;

        public static TruncationStrategy fromString(String v) {
            if ("true".equals(v)) return LONGEST_FIRST;
            else if ("false".equals(v)) return DO_NOT_TRUNCATE;
            return Arrays.stream(values())
                    .filter(s -> s.name().equalsIgnoreCase(v))
                    .findAny().orElseThrow(() -> new IllegalArgumentException("Invalid strategy '%s'".formatted(v)));
        }
    }

    public enum PaddingStrategy {
        LONGEST,
        MAX_LENGTH,
        DO_NOT_PAD;

        public static PaddingStrategy fromString(String v) {
            if ("true".equals(v)) return LONGEST;
            else if ("false".equals(v)) return DO_NOT_PAD;
            return Arrays.stream(values())
                    .filter(s -> s.name().equalsIgnoreCase(v))
                    .findAny().orElseThrow(() -> new IllegalArgumentException("Invalid strategy '%s'".formatted(v)));
        }
    }
}
