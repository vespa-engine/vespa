// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an xor-aggregator in a {@link GroupingExpression}. It evaluates to the xor of the values that
 * the contained expression evaluated to over all the inputs.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class XorAggregator extends AggregatorNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param expression the expression to aggregate on.
     */
    public XorAggregator(GroupingExpression expression) {
        this(null, null, expression);
    }

    private XorAggregator(String label, Integer level, GroupingExpression expression) {
        super("xor", label, level, expression);
    }

    @Override
    public XorAggregator copy() {
        return new XorAggregator(getLabel(), getLevelOrNull(), getExpression().copy());
    }

}
