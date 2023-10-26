// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This function decompose two-dimensonal zcurve values into x and y values.
 *
 * @author baldersheim
 */
public class ZCurveFunctionNode extends UnaryFunctionNode {

    public static enum Dimension {
        X(0),
        Y(1);

        private final int id;

        private Dimension(int id) {
            this.id = id;
        }

        private static Dimension valueOf(int id) {
            for (Dimension dim : values()) {
                if (id == dim.id) {
                    return dim;
                }
            }
            return null;
        }
    }

    public static final int classId = registerClass(0x4000 + 139, ZCurveFunctionNode.class, ZCurveFunctionNode::new);
    private Dimension dim = Dimension.X;

    private ZCurveFunctionNode() {}

    public ZCurveFunctionNode(ExpressionNode arg, Dimension dimension) {
        addArg(arg);
        dim = dimension;
    }

    /**
     * Obtain the predefined bucket list of this bucket expression
     *
     * @return what part of the time you have requested
     */
    public final Dimension getDimension() {
        return dim;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putByte(null, (byte)dim.id);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        int b = buf.getByte(null);
        dim = Dimension.valueOf(b);
    }

    @Override
    protected boolean equalsUnaryFunction(UnaryFunctionNode obj) {
        return dim == ((ZCurveFunctionNode)obj).dim;
    }

    @Override
    public ZCurveFunctionNode clone() {
        ZCurveFunctionNode obj = (ZCurveFunctionNode)super.clone();
        obj.dim = dim;
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("dimension", dim);
    }
}
