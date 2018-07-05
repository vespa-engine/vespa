// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This is an abstract super-class for all functions that accept only a single argument.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public abstract class UnaryFunctionNode extends MultiArgFunctionNode {

    public static final int classId = registerClass(0x4000 + 43, UnaryFunctionNode.class);

    @Override
    protected int onGetClassId() {
        return classId;
    }

    /**
     * Return the single argument given to this function.
     *
     * @return The argument to this function
     */
    public ExpressionNode getArg() {
        return getArg(0);
    }

    @Override
    public void onPrepareResult() {
        setResult((ResultNode)getArg().getResult().clone());
    }

    @Override
    public void onPrepare() {
        super.onPrepare();
    }

    @Override
    protected final boolean equalsMultiArgFunction(MultiArgFunctionNode obj) {
        return equalsUnaryFunction((UnaryFunctionNode)obj);
    }

    protected abstract boolean equalsUnaryFunction(UnaryFunctionNode obj);
}
