// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an average-aggregator in a {@link GroupingExpression}. It evaluates to the average value that
 * the contained expression evaluated to over all the inputs.
 *
 * @author Simon Thoresen Hult
 */
public class AvgAggregator extends AggregatorNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to aggregate on.
     */
    public AvgAggregator(GroupingExpression exp) {
        super("avg", exp);
    }
}
