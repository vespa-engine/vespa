// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an count-aggregator in a {@link GroupingExpression}. It evaluates to the number of elements
 * there are in the input.
 *
 * @author Simon Thoresen Hult
 */
public class CountAggregator extends AggregatorNode {

    /**
     * Constructs a new instance of this class.
     */
    public CountAggregator() {
        super("count");
    }
}
