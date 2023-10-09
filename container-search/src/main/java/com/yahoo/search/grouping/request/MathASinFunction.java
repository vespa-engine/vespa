// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * @author baldersheim
 * @author bratseth
 */
public class MathASinFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, double value will be requested.
     */
    public MathASinFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private MathASinFunction(String label, Integer level, GroupingExpression exp) {
        super("math.asin", label, level, Arrays.asList(exp));
    }

    @Override
    public MathASinFunction copy() {
        return new MathASinFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
