// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a first-aggregator in a {@link GroupingExpression}. It evaluates to the value that the
 * contained expression evaluated to for the first hit in the group, where the first hit is the one that ranks first.
 *
 * @author johsol
 */
public class FirstAggregator extends AggregatorNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param expression the expression to aggregate on.
     */
    public FirstAggregator(GroupingExpression expression) {
        this(null, null, expression);
    }

    private FirstAggregator(String label, Integer level, GroupingExpression expression) {
        super("first", label, level, expression);
    }

    @Override
    public FirstAggregator copy() {
        return new FirstAggregator(getLabel(), getLevelOrNull(), getExpression().copy());
    }

}
