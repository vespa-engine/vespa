// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an stddev-aggregator in a {@link GroupingExpression}. It evaluates to population standard deviation
 * of the values that the contained expression evaluated to over all the inputs.
 *
 * @author bjorncs
 * @author bratseth
 */
public class StandardDeviationAggregator extends AggregatorNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param expression the expression to aggregate on.
     */
    public StandardDeviationAggregator(GroupingExpression expression) {
        this(null, null, expression);
    }

    private StandardDeviationAggregator(String label, Integer level, GroupingExpression expression) {
        super("stddev", label, level, expression);
    }

    @Override
    public StandardDeviationAggregator copy() {
        return new StandardDeviationAggregator(getLabel(), getLevelOrNull(), getExpression().copy());
    }

}
