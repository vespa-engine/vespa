// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Encapsulates values required for predicate fields.
 *
 * @author lesters
 */
public final class BooleanIndexDefinition {

    public static final int DEFAULT_ARITY = 8;
    public static final long DEFAULT_UPPER_BOUND = Long.MAX_VALUE;
    public static final long DEFAULT_LOWER_BOUND = Long.MIN_VALUE;
    public static final double DEFAULT_DENSE_POSTING_LIST_THRESHOLD = 0.4;

    private final OptionalInt arity;   // mandatory field value
    private final OptionalLong lowerBound;
    private final OptionalLong upperBound;
    private final OptionalDouble densePostingListThreshold;

    public BooleanIndexDefinition(Optional<Integer> arity,
                                  Optional<Long> lowerBound,
                                  Optional<Long> upperBound,
                                  Optional<Double> densePLT)
    {
        this.arity                     = arity.isPresent() ? OptionalInt.of(arity.get()) : OptionalInt.empty();
        this.lowerBound                = lowerBound.isPresent() ? OptionalLong.of(lowerBound.get()) : OptionalLong.empty();
        this.upperBound                = upperBound.isPresent() ? OptionalLong.of(upperBound.get()) : OptionalLong.empty();
        this.densePostingListThreshold = densePLT.isPresent() ? OptionalDouble.of(densePLT.get()) : OptionalDouble.empty();
    }

    public BooleanIndexDefinition(OptionalInt arity, OptionalLong lowerBound,
                                  OptionalLong upperBound, OptionalDouble densePostingListThreshold) {
        this.arity = arity;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.densePostingListThreshold = densePostingListThreshold;
    }

    public int getArity() {
        return arity.getAsInt();
    }

    public boolean hasArity() {
        return arity.isPresent();
    }

    public long getLowerBound() {
        return lowerBound.orElse(DEFAULT_LOWER_BOUND);
    }

    public boolean hasLowerBound() {
        return lowerBound.isPresent();
    }

    public long getUpperBound() {
        return upperBound.orElse(DEFAULT_UPPER_BOUND);
    }

    public boolean hasUpperBound() {
        return upperBound.isPresent();
    }

    public double getDensePostingListThreshold() {
        return densePostingListThreshold.orElse(DEFAULT_DENSE_POSTING_LIST_THRESHOLD);
    }

    public boolean hasDensePostingListThreshold() {
        return densePostingListThreshold.isPresent();
    }

    @Override
    public String toString() {
        return "BooleanIndexDefinition [arity=" + arity + ", lowerBound="
                + lowerBound + ", upperBound=" + upperBound + ", densePostingListThreshold="
                + densePostingListThreshold + "]";
    }

}
