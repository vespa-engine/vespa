// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * @author baldersheim
 * @author bratseth
 */
public class ZCurveXFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long or long[].
     */
    public ZCurveXFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private ZCurveXFunction(String label, Integer level, GroupingExpression exp) {
        super("zcurve.x", label, level, Arrays.asList(exp));
    }

    @Override
    public ZCurveXFunction copy() {
        return new ZCurveXFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
