// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a {@link String} bucket in a {@link PredefinedFunction}.
 *
 * @author Simon Thoresen Hult
 */
public class StringBucket extends BucketValue {

    /**
     * Get the next distinct value.
     *
     * @param value the base value
     * @return the next value
     */
    public static StringValue nextValue(StringValue value) {
        return new StringValue(value.getValue() + " ");
    }

    /** Constructs a new bucket for a single unique string */
    public StringBucket(String value) {
        this(new StringValue(value));
    }

    /** Constructs a new bucket for a single unique string */
    public StringBucket(StringValue value) {
        this(value, nextValue(value));
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param from the from-value to assign to this
     * @param to the to-value to assign to this
     */
    public StringBucket(String from, String to) {
        super(null, null, new StringValue(from), new StringValue(to));
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param from the from-value to assign to this
     * @param to the to-value to assign to this
     */
    public StringBucket(ConstantValue<?> from, ConstantValue<?> to) {
        super(null, null, from, to);
    }

    private StringBucket(String label, Integer level, ConstantValue<?> from, ConstantValue<?> to) {
        super(label, level, from, to);
    }

    @Override
    public StringBucket copy() {
        return new StringBucket(getLabel(), getLevelOrNull(), getFrom().copy(), getTo().copy());
    }

}
