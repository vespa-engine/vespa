// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a quantile-aggregator in a {@link GroupingExpression}. It evaluates to the quantile that
 * the contained expression evaluated to over all the inputs.
 *
 * @author johsol
 */
public class QuantileAggregator extends AggregatorNode {

    private double quantile;

    /**
     * Constructs a new instance of this class.
     *
     * @param expression the expression to aggregate on.
     */
    public QuantileAggregator(double quantile, GroupingExpression expression) {
        this(null, null, quantile, expression);
    }

    private QuantileAggregator(String label, Integer level, double quantile, GroupingExpression expression) {
        super("quantile", label, level, expression);
        this.quantile = quantile;
    }

    public double getQuantile() {
        return quantile;
    }

    @Override
    public QuantileAggregator copy() {
        return new QuantileAggregator(getLabel(), getLevelOrNull(), getQuantile(), getExpression().copy());
    }

}
