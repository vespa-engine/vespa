// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function is an instruction to negate its argument.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class ToStringFunctionNode extends UnaryFunctionNode {

    public static final int classId = registerClass(0x4000 + 131, ToStringFunctionNode.class, ToStringFunctionNode::new);

    public ToStringFunctionNode() {}

    /**
     * Constructs an instance of this class with given argument.
     *
     * @param arg The argument for this function.
     */
    public ToStringFunctionNode(ExpressionNode arg) {
        addArg(arg);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    public void onPrepareResult() {
        setResult(new StringResultNode());
    }

    @Override
    public boolean onExecute() {
        getArg().execute();
        ((StringResultNode)getResult()).setValue(getArg().getResult().getString());
        return true;
    }

    @Override
    protected boolean equalsUnaryFunction(UnaryFunctionNode obj) {
        return true;
    }
}
