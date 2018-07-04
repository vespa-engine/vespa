// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function is a request to calculate the MD5 of the result of its argument.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class MD5BitFunctionNode extends UnaryBitFunctionNode {

    public static final int classId = registerClass(0x4000 + 70, MD5BitFunctionNode.class);

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public MD5BitFunctionNode() {

    }

    /**
     * Constructs an instance of this class with given argument and number of bits.
     *
     * @param arg     The argument for this function.
     * @param numBits The number of bits to operate on.
     */
    public MD5BitFunctionNode(ExpressionNode arg, int numBits) {
        super(arg, numBits);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }
}
