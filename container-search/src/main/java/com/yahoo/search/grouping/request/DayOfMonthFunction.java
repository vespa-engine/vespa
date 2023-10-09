// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * This class represents a day-of-month timestamp-function in a {@link GroupingExpression}. It evaluates to a long that
 * equals the day of month (1-31) of the result of the argument.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class DayOfMonthFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     */
    public DayOfMonthFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private DayOfMonthFunction(String label, Integer level, GroupingExpression exp) {
        super("time.dayofmonth", label, level, Arrays.asList(exp));
    }

    @Override
    public DayOfMonthFunction copy() {
        return new DayOfMonthFunction(getLabel(),
                                      getLevelOrNull(),
                                      getArg(0).copy());
    }

}
