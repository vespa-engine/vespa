// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * @author baldersheim
 * @author bratseth
 */
public class MathPowFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param x The expression to evaluate for base, double value will be requested.
     * @param y The expression to evaluate for the exponent, double value will be requested.
     */
    public MathPowFunction(GroupingExpression x, GroupingExpression y) {
        this(null, null, x, y);
    }

    private MathPowFunction(String label, Integer level, GroupingExpression x, GroupingExpression y) {
        super("math.pow", label, level, Arrays.asList(x, y));
    }

    @Override
    public MathPowFunction copy() {
        return new MathPowFunction(getLabel(),
                                   getLevelOrNull(),
                                   getArg(0).copy(),
                                   getArg(1).copy());
    }

}
