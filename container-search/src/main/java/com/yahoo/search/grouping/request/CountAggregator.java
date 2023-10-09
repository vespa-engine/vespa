// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an count-aggregator in a {@link GroupingExpression}. It evaluates to the number of elements
 * there are in the input.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class CountAggregator extends AggregatorNode {

    /** Constructs a new instance of this class. */
    public CountAggregator() {
        this(null, null);
    }

    private CountAggregator(String label, Integer level) {
        super("count", label, level);
    }

    @Override
    public CountAggregator copy() {
        return new CountAggregator(getLabel(), getLevelOrNull());
    }

}
