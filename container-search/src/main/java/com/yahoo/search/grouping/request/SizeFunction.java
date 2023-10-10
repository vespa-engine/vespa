// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a size-function in a {@link GroupingExpression}. It evaluates to a number that equals the
 * number of elements in the result of the argument (e.g. the number of elements in an array).
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class SizeFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate.
     */
    public SizeFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private SizeFunction(String label, Integer level, GroupingExpression exp) {
        super("size", label, level, Arrays.asList(exp));
    }

    @Override
    public SizeFunction copy() {
        return new SizeFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}

