// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * @author baldersheim
 * @author bratseth
 */
public class MathLog10Function extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, double value will be requested.
     */
    public MathLog10Function(GroupingExpression exp) {
        this(null, null, exp);
    }

    private MathLog10Function(String label, Integer level, GroupingExpression exp) {
        super("math.log10", label, level, Arrays.asList(exp));
    }

    @Override
    public MathLog10Function copy() {
        return new MathLog10Function(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
