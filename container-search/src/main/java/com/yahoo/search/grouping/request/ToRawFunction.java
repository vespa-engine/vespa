// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents a toraw-function in a {@link GroupingExpression}. It
 * converts the result of the argument to a raw type. If the argument can not
 * be converted, this function returns null.
 *
 * @author <a href="mailto:lulf@yahoo-inc.com">Ulf Lilleengen</a>
 */
public class ToRawFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate.
     */
    public ToRawFunction(GroupingExpression exp) {
        super("toraw", Arrays.asList(exp));
    }
}
