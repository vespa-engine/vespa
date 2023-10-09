// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a day-of-week timestamp-function in a {@link GroupingExpression}. It evaluates to a long that
 * equals the day of week (0 - 6) of the result of the argument, Monday being 0.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class DayOfWeekFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     */
    public DayOfWeekFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private DayOfWeekFunction(String label, Integer level, GroupingExpression exp) {
        super("time.dayofweek", label, level, Arrays.asList(exp));
    }

    @Override
    public DayOfWeekFunction copy() {
        return new DayOfWeekFunction(getLabel(),
                                      getLevelOrNull(),
                                      getArg(0).copy());
    }

}
