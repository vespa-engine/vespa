// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an sum-aggregator in a {@link GroupingExpression}. It evaluates to the sum of the values that
 * the contained expression evaluated to over all the inputs.
 *
 * @author Simon Thoresen Hult
 */
public class SumAggregator extends AggregatorNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to aggregate on.
     */
    public SumAggregator(GroupingExpression exp) {
        super("sum", exp);
    }
}
