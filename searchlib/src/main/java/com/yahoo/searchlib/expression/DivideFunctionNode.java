// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function is an instruction to divide the arguments in order.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class DivideFunctionNode extends NumericFunctionNode {

    public static final int classId = registerClass(0x4000 + 63, DivideFunctionNode.class, DivideFunctionNode::new);

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onArgument(final ResultNode arg, ResultNode result) {
        ((NumericResultNode)result).divide(arg);
    }
}
