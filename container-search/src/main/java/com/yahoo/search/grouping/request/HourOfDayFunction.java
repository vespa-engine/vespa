// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents an hour-of-day timestamp-function in a {@link GroupingExpression}. It evaluates to a long that
 * equals the hour of day (0-23) of the result of the argument.
 *
 * @author Simon Thoresen Hult
 */
public class HourOfDayFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     */
    public HourOfDayFunction(GroupingExpression exp) {
        super("time.hourofday", Arrays.asList(exp));
    }
}
