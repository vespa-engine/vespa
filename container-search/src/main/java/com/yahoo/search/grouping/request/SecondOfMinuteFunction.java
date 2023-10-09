// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a second-of-minute timestamp-function in a {@link GroupingExpression}. It evaluates to a long
 * that equals the second of minute (0-59) of the result of the argument.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class SecondOfMinuteFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     */
    public SecondOfMinuteFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private SecondOfMinuteFunction(String label, Integer level, GroupingExpression exp) {
        super("time.secondofminute", label, level, Arrays.asList(exp));
    }

    @Override
    public SecondOfMinuteFunction copy() {
        return new SecondOfMinuteFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
