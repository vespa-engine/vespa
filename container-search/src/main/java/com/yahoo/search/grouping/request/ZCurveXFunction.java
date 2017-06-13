// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * @author baldersheim
 */
public class ZCurveXFunction extends FunctionNode {
    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long or long[].
     */
    public ZCurveXFunction(GroupingExpression exp) {
        super("zcurve.x", Arrays.asList(exp));
    }
}
