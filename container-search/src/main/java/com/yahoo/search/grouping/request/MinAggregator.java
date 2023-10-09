// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an minimum-aggregator in a {@link GroupingExpression}. It evaluates to the minimum value that
 * the contained expression evaluated to over all the inputs.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class MinAggregator extends AggregatorNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param expression the expression to aggregate on.
     */
    public MinAggregator(GroupingExpression expression) {
        this(null, null, expression);
    }

    private MinAggregator(String label, Integer level, GroupingExpression expression) {
        super("min", label, level, expression);
    }

    @Override
    public MinAggregator copy() {
        return new MinAggregator(getLabel(), getLevelOrNull(), getExpression().copy());
    }

}
