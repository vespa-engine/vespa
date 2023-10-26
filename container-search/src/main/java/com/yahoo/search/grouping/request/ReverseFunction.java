// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a reverse-function in a {@link GroupingExpression}. It evaluates to a list that equals the list
 * result of the argument, sorted in descending order.
 *
 * @author baldersheim
 * @author bratseth
 */
public class ReverseFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a list.
     */
    public ReverseFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private ReverseFunction(String label, Integer level, GroupingExpression exp) {
        super("reverse", label, level, Arrays.asList(exp));
    }

    @Override
    public ReverseFunction copy() {
        return new ReverseFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
