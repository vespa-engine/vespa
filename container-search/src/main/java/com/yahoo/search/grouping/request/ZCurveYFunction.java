// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * @author baldersheim
 * @author bratseth
 */
public class ZCurveYFunction extends FunctionNode {
    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long or long[].
     */
    public ZCurveYFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private ZCurveYFunction(String label, Integer level, GroupingExpression exp) {
        super("zcurve.y", label, level, Arrays.asList(exp));
    }

    @Override
    public ZCurveYFunction copy() {
        return new ZCurveYFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}
