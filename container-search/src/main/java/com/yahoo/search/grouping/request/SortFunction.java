// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a sort-function in a {@link GroupingExpression}. It evaluates to a list that equals the list
 * result of the argument, sorted in ascending order.
 *
 * @author baldersheim
 * @author bratseth
 */
public class SortFunction  extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a list.
     */
    public SortFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private SortFunction(String label, Integer level, GroupingExpression exp) {
        super("sort", label, level, Arrays.asList(exp));
    }

    @Override
    public SortFunction copy() {
        return new SortFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
