// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a fixed-width bucket-function in a {@link GroupingExpression}. It maps the input into the given
 * number of buckets by the result of the argument expression.
 *
 * @author Simon Thoresen Hult
 */
public class FixedWidthFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp        The expression to evaluate.
     * @param width The width of each bucket.
     */
    public FixedWidthFunction(GroupingExpression exp, Number width) {
        this(null, null, exp,
             width instanceof Double ? new DoubleValue(width.doubleValue()) : new LongValue(width.longValue()));
    }

    private FixedWidthFunction(String label, Integer level, GroupingExpression exp, ConstantValue width) {
        super("fixedwidth", label, level, Arrays.asList(exp, width));
    }

    @Override
    public FixedWidthFunction copy() {
        return new FixedWidthFunction(getLabel(), getLevelOrNull(), getArg(0).copy(), (ConstantValue)getArg(1).copy());
    }

    /**
     * Returns the number of buckets to divide the result into.
     *
     * @return The bucket count.
     */
    public Number getWidth() {
        GroupingExpression w = getArg(1);
        return (w instanceof LongValue) ? ((LongValue)w).getValue() : ((DoubleValue)w).getValue();
    }
}

