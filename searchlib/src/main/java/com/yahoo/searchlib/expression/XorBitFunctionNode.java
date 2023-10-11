// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This function is a request to bitwise XOR the result of its first argument with itself in chunks of the second
 * argument number of bits. If the result to XOR is a 24 bit value, and the second argument is 8, this function will XOR
 * the first 8 bits of the result with the next 8 bits of the result, and then XOR that number with the next 8 bits of
 * the result.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class XorBitFunctionNode extends UnaryBitFunctionNode {

    public static final int classId = registerClass(0x4000 + 71, XorBitFunctionNode.class, XorBitFunctionNode::new);

    public XorBitFunctionNode() {}

    /**
     * Constructs an instance of this class with given argument and number of bits.
     *
     * @param arg     The argument for this function.
     * @param numBits The number of bits to operate on.
     */
    public XorBitFunctionNode(ExpressionNode arg, int numBits) {
        super(arg, numBits);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }
}
