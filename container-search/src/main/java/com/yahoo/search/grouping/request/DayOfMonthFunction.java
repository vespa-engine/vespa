// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a day-of-month timestamp-function in a {@link GroupingExpression}. It evaluates to a long that
 * equals the day of month (1-31) of the result of the argument.
 *
 * @author Simon Thoresen Hult
 */
public class DayOfMonthFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     */
    public DayOfMonthFunction(GroupingExpression exp) {
        super("time.dayofmonth", Arrays.asList(exp));
    }
}
