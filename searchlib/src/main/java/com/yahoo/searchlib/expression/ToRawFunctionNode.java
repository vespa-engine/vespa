// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function converts its argument to a raw function node.
 *
 * @author Ulf Lilleengen
 */
public class ToRawFunctionNode extends UnaryFunctionNode {

    public static final int classId = registerClass(0x4000 + 141, ToRawFunctionNode.class, ToRawFunctionNode::new);

    public ToRawFunctionNode() {}

    /**
     * Constructs an instance of this class with given argument.
     *
     * @param arg The argument for this function.
     */
    public ToRawFunctionNode(ExpressionNode arg) {
        addArg(arg);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected boolean equalsUnaryFunction(UnaryFunctionNode obj) {
        return true;
    }
}
