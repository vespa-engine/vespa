// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a negate-function in a {@link GroupingExpression}. It evaluates to a number that equals the
 * negative of the results of the argument.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class NegFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a number.
     */
    public NegFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private NegFunction(String label, Integer level, GroupingExpression exp) {
        super("neg", label, level, Arrays.asList(exp));
    }

    @Override
    public NegFunction copy() {
        return new NegFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}

