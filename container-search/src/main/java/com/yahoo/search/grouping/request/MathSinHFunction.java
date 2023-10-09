// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * @author baldersheim
 * @author bratseth
 */
public class MathSinHFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, double value will be requested.
     */
    public MathSinHFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private MathSinHFunction(String label, Integer level, GroupingExpression exp) {
        super("math.sinh", label, level, Arrays.asList(exp));
    }

    @Override
    public MathSinHFunction copy() {
        return new MathSinHFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
