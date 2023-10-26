// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public class RangeBucketPreDefFunctionNode extends UnaryFunctionNode {

    public static final int classId = registerClass(0x4000 + 76, RangeBucketPreDefFunctionNode.class, RangeBucketPreDefFunctionNode::new);
    private ResultNodeVector predef = null;

    public RangeBucketPreDefFunctionNode() {}

    /**
     * Create a bucket expression with the given width and the given subexpression
     *
     * @param v   predefined bucket list
     * @param arg The argument for this function.
     */
    public RangeBucketPreDefFunctionNode(ResultNodeVector v, ExpressionNode arg) {
        addArg(arg);
        predef = v;
    }

    /**
     * Obtain the predefined bucket list of this bucket expression
     *
     * @return predefined bucket list for this expression
     */
    public ResultNodeVector getBucketList() {
        return predef;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        serializeOptional(buf, predef);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        predef = (ResultNodeVector)deserializeOptional(buf);
    }

    @Override
    protected boolean equalsUnaryFunction(UnaryFunctionNode obj) {
        return equals(predef, ((RangeBucketPreDefFunctionNode)obj).predef);
    }

    @Override
    public RangeBucketPreDefFunctionNode clone() {
        RangeBucketPreDefFunctionNode obj = (RangeBucketPreDefFunctionNode)super.clone();
        if (predef != null) {
            obj.predef = (ResultNodeVector)predef.clone();
        }
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("predef", predef);
    }
}
