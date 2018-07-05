// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function is an instruction to perform bitwise XOR on the result of all arguments in order.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class XorFunctionNode extends BitFunctionNode {

    public static final int classId = registerClass(0x4000 + 69, XorFunctionNode.class);

    @Override
    protected int onGetClassId() {
        return classId;
    }

    public void onArgument(final ResultNode arg, IntegerResultNode result) {
        result.xorOp(arg);
    }
}
