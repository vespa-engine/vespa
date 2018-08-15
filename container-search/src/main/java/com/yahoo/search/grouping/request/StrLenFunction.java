// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a strcat-function in a {@link GroupingExpression}. It evaluates to a long that equals the
 * number of bytes in the string result of the argument.
 *
 * @author Simon Thoresen Hult
 */
public class StrLenFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a string.
     */
    public StrLenFunction(GroupingExpression exp) {
        super("strlen", Arrays.asList(exp));
    }
}

