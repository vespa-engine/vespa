// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a {@link Long} bucket in a {@link PredefinedFunction}.
 *
 * @author Simon Thoresen Hult
 */
public class LongBucket extends BucketValue {

    /**
     * Gives the next distinct long value.
     *
     * @param value the base value.
     * @return the next value.
     */
    public static LongValue nextValue(LongValue value) {
        long v = value.getValue();
        return new LongValue(v < Long.MAX_VALUE ? v + 1 : v);
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param from          The from-value to assign to this.
     * @param to            The to-value to assign to this.
     */
    public LongBucket(long from, long to) {
        super(null, null, new LongValue(from), new LongValue(to));
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param from          The from-value to assign to this.
     * @param to            The to-value to assign to this.
     */
    @SuppressWarnings("rawtypes")
    public LongBucket(ConstantValue from, ConstantValue to) {
        super(null, null, from, to);
    }

    private LongBucket(String label, Integer level, ConstantValue<?> from, ConstantValue<?> to) {
        super(label, level, from, to);
    }

    @Override
    public LongBucket copy() {
        return new LongBucket(getLabel(), getLevelOrNull(), getFrom().copy(), getTo().copy());
    }

}
