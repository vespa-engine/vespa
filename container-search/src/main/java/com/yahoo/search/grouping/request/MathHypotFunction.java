// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * @author baldersheim
 * @author bratseth
 */
public class MathHypotFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param x The expression to evaluate for x, double value will be requested.
     * @param y The expression to evaluate for y exponent, double value will be requested.
     */
    public MathHypotFunction(GroupingExpression x, GroupingExpression y) {
        this(null, null, x, y);
    }

    private MathHypotFunction(String label, Integer level, GroupingExpression x, GroupingExpression y) {
        super("math.hypot", label, level, Arrays.asList(x, y));
    }

    @Override
    public MathHypotFunction copy() {
        return new MathHypotFunction(getLabel(), getLevelOrNull(), getArg(0).copy(), getArg(1).copy());
    }

}
