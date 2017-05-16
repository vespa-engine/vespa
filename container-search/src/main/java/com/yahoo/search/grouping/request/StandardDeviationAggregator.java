// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an stddev-aggregator in a {@link GroupingExpression}. It evaluates to population standard deviation
 * of the values that the contained expression evaluated to over all the inputs.
 *
 * @author bjorncs
 */
public class StandardDeviationAggregator extends AggregatorNode {

    /**
     * @param exp The expression to aggregate on.
     */
    public StandardDeviationAggregator(GroupingExpression exp) {
        super("stddev", exp);
    }
}
