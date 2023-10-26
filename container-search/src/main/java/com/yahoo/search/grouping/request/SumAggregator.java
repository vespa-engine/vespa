// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an sum-aggregator in a {@link GroupingExpression}. It evaluates to the sum of the values that
 * the contained expression evaluated to over all the inputs.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class SumAggregator extends AggregatorNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param expression the expression to aggregate on.
     */
    public SumAggregator(GroupingExpression expression) {
        this(null, null, expression);
    }

    private SumAggregator(String label, Integer level, GroupingExpression expression) {
        super("sum", label, level, expression);
    }

    @Override
    public SumAggregator copy() {
        return new SumAggregator(getLabel(), getLevelOrNull(), getExpression().copy());
    }

}
