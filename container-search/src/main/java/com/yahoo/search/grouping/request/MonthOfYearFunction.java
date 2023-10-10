// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a month-of-year timestamp-function in a {@link GroupingExpression}. It evaluates to a long that
 * equals the month of year (1-12) of the result of the argument.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class MonthOfYearFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     */
    public MonthOfYearFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private MonthOfYearFunction(String label, Integer level, GroupingExpression exp) {
        super("time.monthofyear", label, level, Arrays.asList(exp));
    }

    @Override
    public MonthOfYearFunction copy() {
        return new MonthOfYearFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
