// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an maximum-aggregator in a {@link GroupingExpression}. It evaluates to the maximum value that
 * the contained expression evaluated to over all the inputs.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class MaxAggregator extends AggregatorNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param expression the expression to aggregate on.
     */
    public MaxAggregator(GroupingExpression expression) {
        this(null, null, expression);
    }

    private MaxAggregator(String label, Integer level, GroupingExpression expression) {
        super("max", label, level, expression);
    }

    @Override
    public MaxAggregator copy() {
        return new MaxAggregator(getLabel(), getLevelOrNull(), getExpression().copy());
    }

}
