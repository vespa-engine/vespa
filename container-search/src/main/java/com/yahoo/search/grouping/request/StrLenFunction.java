// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a strcat-function in a {@link GroupingExpression}. It evaluates to a long that equals the
 * number of bytes in the string result of the argument.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class StrLenFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a string.
     */
    public StrLenFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private StrLenFunction(String label, Integer level, GroupingExpression exp) {
        super("strlen", label, level, Arrays.asList(exp));
    }

    @Override
    public StrLenFunction copy() {
        return new StrLenFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}

