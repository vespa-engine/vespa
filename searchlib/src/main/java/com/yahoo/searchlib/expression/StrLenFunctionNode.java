// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function is an instruction to negate its argument.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class StrLenFunctionNode extends UnaryFunctionNode {

    public static final int classId = registerClass(0x4000 + 130, StrLenFunctionNode.class, StrLenFunctionNode::new);

    public StrLenFunctionNode() {}

    /**
     * Constructs an instance of this class with given argument.
     *
     * @param arg The argument for this function.
     */
    public StrLenFunctionNode(ExpressionNode arg) {
        addArg(arg);
    }

    @Override
    public void onPrepareResult() {
        setResult(new IntegerResultNode(0));
    }

    @Override
    public void onPrepare() {
        super.onPrepare();
    }

    @Override
    public boolean onExecute() {
        ((IntegerResultNode)getResult()).setValue(getArg().getResult().getString().length());
        return true;
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
