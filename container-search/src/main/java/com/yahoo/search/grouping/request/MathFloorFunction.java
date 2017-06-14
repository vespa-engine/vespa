// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/** represents the math.floor(expression) function */
public class MathFloorFunction extends FunctionNode {
    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, double value will be requested.
     */
    public MathFloorFunction(GroupingExpression exp) {
        super("math.floor", Arrays.asList(exp));
    }
}
