// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function is an instruction to perform bitwise OR on the result of all arguments.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class OrFunctionNode extends BitFunctionNode {

    public static final int classId = registerClass(0x4000 + 68, OrFunctionNode.class, OrFunctionNode::new);

    @Override
    protected int onGetClassId() {
        return classId;
    }

    public void onArgument(final ResultNode arg, IntegerResultNode result) {
        result.orOp(arg);
    }
}
