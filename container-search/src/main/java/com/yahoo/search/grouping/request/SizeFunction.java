// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a size-function in a {@link GroupingExpression}. It evaluates to a number that equals the
 * number of elements in the result of the argument (e.g. the number of elements in an array).
 *
 * @author Simon Thoresen Hult
 */
public class SizeFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate.
     */
    public SizeFunction(GroupingExpression exp) {
        super("size", Arrays.asList(exp));
    }
}

