// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a tolong-function in a {@link GroupingExpression}. It converts the result of the argument to a
 * long. If the argument can not be converted, this function returns 0.
 *
 * @author baldersheim
 * @author bratseth
 */
public class ToStringFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate.
     */
    public ToStringFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private ToStringFunction(String label, Integer level, GroupingExpression exp) {
        super("tostring", label, level, Arrays.asList(exp));
    }

    @Override
    public ToStringFunction copy() {
        return new ToStringFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
