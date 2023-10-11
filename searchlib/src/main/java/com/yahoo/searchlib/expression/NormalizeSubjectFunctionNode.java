// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function is an instruction to negate its argument.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class NormalizeSubjectFunctionNode extends UnaryFunctionNode {

    public static final int classId = registerClass(0x4000 + 143, NormalizeSubjectFunctionNode.class, NormalizeSubjectFunctionNode::new);

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public NormalizeSubjectFunctionNode() {

    }

    /**
     * Constructs an instance of this class with given argument.
     *
     * @param arg The argument for this function.
     */
    public NormalizeSubjectFunctionNode(ExpressionNode arg) {
        addArg(arg);
    }

    @Override
    public void onPrepareResult() {
        setResult(new StringResultNode());
    }

    @Override
    public void onPrepare() {
        super.onPrepare();
    }

    @Override
    public boolean onExecute() {
        String result = getArg().getResult().getString();

        if (result.startsWith("Re: ") || result.startsWith("RE: ") || result.startsWith("Fw: ") ||
            result.startsWith("FW: "))
        {
            result = result.substring(4);
        } else if (result.startsWith("Fwd: ")) {
            result = result.substring(5);
        }

        ((StringResultNode)getResult()).setValue(result);
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
