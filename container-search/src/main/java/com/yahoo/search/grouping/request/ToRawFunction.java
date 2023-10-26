// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a toraw-function in a {@link GroupingExpression}. It
 * converts the result of the argument to a raw type. If the argument can not
 * be converted, this function returns null.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public class ToRawFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate.
     */
    public ToRawFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private ToRawFunction(String label, Integer level, GroupingExpression exp) {
        super("toraw", label, level, Arrays.asList(exp));
    }

    @Override
    public ToRawFunction copy() {
        return new ToRawFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
