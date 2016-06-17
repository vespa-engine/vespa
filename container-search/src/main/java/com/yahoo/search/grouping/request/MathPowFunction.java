// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * @author baldersheim
 */
public class MathPowFunction extends FunctionNode {
    /**
     * Constructs a new instance of this class.
     *
     * @param x The expression to evaluate for base, double value will be requested.
     * @param y The expression to evaluate for the exponent, double value will be requested.
     */
    public MathPowFunction(GroupingExpression x, GroupingExpression y) {
        super("math.pow", Arrays.asList(x,y));
    }
}
