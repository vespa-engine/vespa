// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is an abstract super-class for all unary functions that operator on bit values.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public abstract class UnaryBitFunctionNode extends UnaryFunctionNode {

    public static final int classId = registerClass(0x4000 + 46, UnaryBitFunctionNode.class);
    private int numBits = 0;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public UnaryBitFunctionNode() {}

    /**
     * Constructs an instance of this class with given argument and number of bits.
     *
     * @param arg     The argument for this function.
     * @param numBits The number of bits to operate on.
     */
    public UnaryBitFunctionNode(ExpressionNode arg, int numBits) {
        addArg(arg);
        setNumBits(numBits);
    }

    /**
     * Returns the number of bits to operate on.
     *
     * @return The number of bits.
     */
    public final int getNumBits() {
        return numBits;
    }

    /**
     * Sets the number of bits to operate on.
     *
     * @param numBits The number of bits.
     * @return This, to allow chaining.
     */
    public UnaryBitFunctionNode setNumBits(int numBits) {
        this.numBits = numBits;
        return this;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putInt(null, numBits);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        numBits = buf.getInt(null);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + numBits;
    }

    @Override
    protected final boolean equalsUnaryFunction(UnaryFunctionNode obj) {
        return numBits == ((UnaryBitFunctionNode)obj).numBits;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("numBits", numBits);
    }
}
