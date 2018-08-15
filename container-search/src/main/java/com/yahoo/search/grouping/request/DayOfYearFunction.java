// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a day-of-year timestamp-function in a {@link GroupingExpression}. It evaluates to a long that
 * equals the day of year (0-365) of the result of the argument.
 *
 * @author Simon Thoresen Hult
 */
public class DayOfYearFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     */
    public DayOfYearFunction(GroupingExpression exp) {
        super("time.dayofyear", Arrays.asList(exp));
    }
}
