// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;
import java.text.ChoiceFormat;

/**
 * This class represents a {@link Double} bucket in a {@link PredefinedFunction}.
 *
 * @author Simon Thoresen Hult
 */
public class DoubleBucket extends BucketValue {

    /**
     * Returns the next distinct value.
     *
     * @param value The base value.
     * @return the next value.
     */
    public static DoubleValue nextValue(DoubleValue value) {
        return (new DoubleValue(ChoiceFormat.nextDouble(value.getValue())));
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param from          The from-value to assign to this.
     * @param to            The to-value to assign to this.
     */
    public DoubleBucket(double from, double to) {
        super(null, null, new DoubleValue(from), new DoubleValue(to));
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param from          The from-value to assign to this.
     * @param to            The to-value to assign to this.
     */
    public DoubleBucket(ConstantValue<?> from, ConstantValue<?> to) {
        super(null, null, from, to);
    }

    private DoubleBucket(String label, Integer level, ConstantValue<?> from, ConstantValue<?> to) {
        super(label, level, from, to);
    }

    @Override
    public DoubleBucket copy() {
        return new DoubleBucket(getLabel(), getLevelOrNull(), getFrom().copy(), getTo().copy());
    }

}
