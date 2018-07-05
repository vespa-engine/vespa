// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function is an instruction to return the maximum value of all its arguments.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class MaxFunctionNode extends NumericFunctionNode {

    public static final int classId = registerClass(0x4000 + 66, MaxFunctionNode.class);

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onArgument(final ResultNode arg, ResultNode result) {
        ((NumericResultNode)result).max(arg);
    }
}
