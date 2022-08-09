// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * A slice of nodes, satisfied by either a minimum count or a fraction.
 *
 * @author mpolden
 */
public record NodeSlice(OptionalDouble fraction, OptionalLong minCount) {

    public static final NodeSlice ALL = minCount(Long.MAX_VALUE);

    public NodeSlice {
        Objects.requireNonNull(fraction);
        Objects.requireNonNull(minCount);
        if (fraction.isEmpty() == minCount.isEmpty()) {
            throw new IllegalArgumentException("Exactly one of 'fraction' or 'minCount' must be set");
        }
        if (fraction.isPresent() && fraction.getAsDouble() > 1.0D) {
            throw new IllegalArgumentException("Fraction must be <= 1.0, got " + fraction.getAsDouble());
        }
    }

    /** Returns whether this slice is satisfied by given node count, out of totalCount */
    public boolean satisfiedBy(long count, long totalCount) {
        if (fraction.isPresent()) {
            return count + 1e-9 >= totalCount * fraction.getAsDouble();
        }
        return count >= Math.min(minCount.orElse(0), totalCount);
    }

    /** Returns a slice matching the given fraction of nodes */
    public static NodeSlice fraction(double fraction) {
        return new NodeSlice(OptionalDouble.of(fraction), OptionalLong.empty());
    }

    /** Returns a slice matching the given minimum number of nodes */
    public static NodeSlice minCount(long count) {
        return new NodeSlice(OptionalDouble.empty(), OptionalLong.of(count));
    }

}
