// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import java.util.OptionalLong;

/**
 * This class contains the configured options for predicate indexes.
 * The adjusted bounds are extended to the nearest power of the arity (-1)
 * and are used to generate more efficient indexes.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class PredicateOptions {

    public static final long DEFAULT_LOWER_BOUND = 0x8000000000000000L;
    public static final long DEFAULT_UPPER_BOUND = 0x7fffffffffffffffL;

    private final int arity;
    private final long lowerBound;
    private final long upperBound;
    private OptionalLong adjustedLowerBound;
    private OptionalLong adjustedUpperBound;

    public PredicateOptions(int arity, Long lowerBound, Long upperBound) {
        this.arity = arity;
        this.lowerBound = lowerBound == null? DEFAULT_LOWER_BOUND : lowerBound;
        this.upperBound = upperBound == null? DEFAULT_UPPER_BOUND : upperBound;
        this.adjustedLowerBound = OptionalLong.empty();
        this.adjustedUpperBound = OptionalLong.empty();
    }

    public PredicateOptions(int arity) {
        this(arity, DEFAULT_LOWER_BOUND, DEFAULT_UPPER_BOUND);
    }

    public int getArity() {
        return arity;
    }

    public long getLowerBound() {
        return lowerBound;
    }

    public long getUpperBound() {
        return upperBound;
    }

    public long getAdjustedLowerBound() {
        if (!adjustedLowerBound.isPresent()) {
            if (lowerBound == DEFAULT_LOWER_BOUND) {
                adjustedLowerBound = OptionalLong.of(lowerBound);
            } else if (lowerBound > 0) {
                adjustedLowerBound = OptionalLong.of(0L);
            } else {
                adjustedLowerBound = OptionalLong.of(-adjustBound(arity, -lowerBound));
            }
        }
        return adjustedLowerBound.getAsLong();
    }

    public long getAdjustedUpperBound() {
        if (!adjustedUpperBound.isPresent()) {
            if (upperBound == DEFAULT_UPPER_BOUND) {
                adjustedUpperBound = OptionalLong.of(DEFAULT_UPPER_BOUND);
            } else if (upperBound < 0) {
                adjustedUpperBound = OptionalLong.of(-1L);  // 0 belongs to the positive range.
            } else {
                adjustedUpperBound = OptionalLong.of(adjustBound(arity, upperBound));
            }
        }
        return adjustedUpperBound.getAsLong();
    }

    private static long adjustBound(int arity, long bound) {
        long adjusted = arity;
        long value = bound;
        long max = Long.MAX_VALUE / arity;
        while ((value/=arity) > 0) {
            if (adjusted > max) {
                return bound;
            }
            adjusted *= arity;
        }
        return adjusted - 1;
    }
}

