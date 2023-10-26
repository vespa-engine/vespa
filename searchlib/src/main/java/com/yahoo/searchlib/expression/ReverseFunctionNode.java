// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This class will revert the order on any multivalues. Nothing is done to single value types such as integers, float,
 * strings and Raw values.
 *
 * @author baldersheim
 */
public class ReverseFunctionNode extends UnaryFunctionNode {

    public static final int classId = registerClass(0x4000 + 138, ReverseFunctionNode.class, ReverseFunctionNode::new);

    public ReverseFunctionNode() {}

    /**
     * Constructs an instance of this class with given argument.
     *
     * @param arg The argument for this function.
     */
    public ReverseFunctionNode(ExpressionNode arg) {
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
