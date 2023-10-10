// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a constant {@link Double} value in a {@link GroupingExpression}.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class DoubleValue extends ConstantValue<Double> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The immutable value to assign to this.
     */
    public DoubleValue(double value) {
        super(null, null, value);
    }

   private DoubleValue(String label, Integer level, Double value) {
        super(label, level, value);
    }

    @Override
    public DoubleValue copy() {
        return new DoubleValue(getLabel(), getLevelOrNull(), getValue());
    }

}
