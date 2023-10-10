// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a minute-of-hour timestamp-function in a {@link GroupingExpression}. It evaluates to a long
 * that equals the minute of hour (0-59) of the result of the argument.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class MinuteOfHourFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     */
    public MinuteOfHourFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private MinuteOfHourFunction(String label, Integer level, GroupingExpression exp) {
        super("time.minuteofhour", label, level, Arrays.asList(exp));
    }

    @Override
    public MinuteOfHourFunction copy() {
        return new MinuteOfHourFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
