// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a {@link String} bucket in a {@link PredefinedFunction}.
 *
 * @author Simon Thoresen Hult
 */
public class StringBucket extends BucketValue {

    /** Returns the next distinct value after the given value */
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
     * Constructs a new bucket for a range of strings.
     *
     * @param from the start of the bucket, inclusive
     * @param to the end of the bucket, exclusive
     */
    public StringBucket(String from, String to) {
        this(from, to, false);
    }

    /**
     * Constructs a new bucket for a range of strings.
     *
     * @param from the start of the bucket, inclusive
     * @param to the end of the bucket
     * @param toInclusive whether <code>to</code> value should be included in the bucket
     */
    public StringBucket(String from, String to, boolean toInclusive) {
        super(null,
              null,
              new StringValue(from),
              toInclusive ? nextValue(new StringValue(to)) : new StringValue(to));
    }

    /**
     * Constructs a new bucket for a range of strings.
     *
     * @param from the start of the bucket, inclusive
     * @param to the end of the bucket, exclusive
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
