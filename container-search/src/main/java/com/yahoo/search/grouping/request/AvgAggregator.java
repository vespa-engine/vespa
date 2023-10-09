// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an average-aggregator in a {@link GroupingExpression}. It evaluates to the average value that
 * the contained expression evaluated to over all the inputs.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class AvgAggregator extends AggregatorNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param expression the expression to aggregate on.
     */
    public AvgAggregator(GroupingExpression expression) {
        this(null, null, expression);
    }

    private AvgAggregator(String label, Integer level, GroupingExpression expression) {
        super("avg", label, level, expression);
    }

    @Override
    public AvgAggregator copy() {
        return new AvgAggregator(getLabel(), getLevelOrNull(), getExpression().copy());
    }

}
