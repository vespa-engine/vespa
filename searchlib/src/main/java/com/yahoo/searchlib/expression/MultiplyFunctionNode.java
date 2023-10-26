// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function is an instruction to multiply all arguments.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class MultiplyFunctionNode extends NumericFunctionNode {

    public static final int classId = registerClass(0x4000 + 62, MultiplyFunctionNode.class, MultiplyFunctionNode::new);

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onArgument(final ResultNode arg, ResultNode result) {
        ((NumericResultNode)result).multiply(arg);
    }
}
