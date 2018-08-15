// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a year timestamp-function in a {@link GroupingExpression}. It evaluates to a long that equals
 * the full year (e.g. 2010) of the result of the argument.
 *
 * @author Simon Thoresen Hult
 */
public class YearFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     */
    public YearFunction(GroupingExpression exp) {
        super("time.year", Arrays.asList(exp));
    }
}
