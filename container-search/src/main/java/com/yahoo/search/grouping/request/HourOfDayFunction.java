// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents an hour-of-day timestamp-function in a {@link GroupingExpression}. It evaluates to a long that
 * equals the hour of day (0-23) of the result of the argument.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class HourOfDayFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     */
    public HourOfDayFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private HourOfDayFunction(String label, Integer level, GroupingExpression exp) {
        super("time.hourofday", label, level, Arrays.asList(exp));
    }

    @Override
    public HourOfDayFunction copy() {
        return new HourOfDayFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
