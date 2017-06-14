// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a {@link RawValue} bucket in a {@link PredefinedFunction}.
 *
 * @author <a href="mailto:lulf@yahoo-inc.com">Ulf Lilleengen</a>
 */
public class RawBucket extends BucketValue {

    /**
     * Get the next distinct value.
     *
     * @param value The base value.
     * @return the next value.
     */
    public static RawValue nextValue(RawValue value) {
        return new RawValue(value.getValue().clone().put((byte)0));
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param from          The from-value to assign to this.
     * @param to            The to-value to assign to this.
     */
    public RawBucket(RawBuffer from, RawBuffer to) {
        super(new RawValue(from), new RawValue(to));
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param from          The from-value to assign to this.
     * @param to            The to-value to assign to this.
     */
    public RawBucket(ConstantValue<?> from, ConstantValue<?> to) {
        super(from, to);
    }
}
