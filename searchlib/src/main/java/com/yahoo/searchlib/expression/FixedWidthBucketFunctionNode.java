// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This function assign a fixed width bucket to each input value
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 * @author Simon Thoresen Hult
 */
public class FixedWidthBucketFunctionNode extends UnaryFunctionNode {

    public static final int classId = registerClass(0x4000 + 77, FixedWidthBucketFunctionNode.class);
    private NumericResultNode width = null;

    /**
     * Constructs an empty result node.
     */
    public FixedWidthBucketFunctionNode() {
        // empty
    }

    /**
     * Create a bucket expression with the given width and the given subexpression
     *
     * @param w   bucket width
     * @param arg The argument for this function.
     */
    public FixedWidthBucketFunctionNode(NumericResultNode w, ExpressionNode arg) {
        addArg(arg);
        width = w;
    }

    /**
     * Obtain the width of this bucket expression
     *
     * @return bucket width for this expression
     */
    public NumericResultNode getWidth() {
        return width;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        serializeOptional(buf, width);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        width = (NumericResultNode)deserializeOptional(buf);
    }

    @Override
    protected boolean equalsUnaryFunction(UnaryFunctionNode obj) {
        return equals(width, ((FixedWidthBucketFunctionNode)obj).width);
    }

    @Override
    public FixedWidthBucketFunctionNode clone() {
        FixedWidthBucketFunctionNode obj = (FixedWidthBucketFunctionNode)super.clone();
        if (width != null) {
            obj.width = (NumericResultNode)width.clone();
        }
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("width", width);
    }
}
