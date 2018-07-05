// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function is an instruction to negate its argument.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class ToIntFunctionNode extends UnaryFunctionNode {

    public static final int classId = registerClass(0x4000 + 135, ToIntFunctionNode.class);

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public ToIntFunctionNode() {

    }

    /**
     * Constructs an instance of this class with given argument.
     *
     * @param arg The argument for this function.
     */
    public ToIntFunctionNode(ExpressionNode arg) {
        addArg(arg);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    public void onPrepareResult() {
        setResult(new IntegerResultNode());
    }

    @Override
    protected boolean equalsUnaryFunction(UnaryFunctionNode obj) {
        return true;
    }
}
