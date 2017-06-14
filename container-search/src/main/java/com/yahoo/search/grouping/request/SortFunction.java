// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a sort-function in a {@link GroupingExpression}. It evaluates to a list that equals the list
 * result of the argument, sorted in ascending order.
 *
 * @author baldersheim
 */
public class SortFunction  extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a list.
     */
    public SortFunction(GroupingExpression exp) {
        super("sort", Arrays.asList(exp));
    }
}
