// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This node is a request to retrieve the YMUM checksum of a document.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class GetYMUMChecksumFunctionNode extends DocumentAccessorNode {

    public static final int classId = registerClass(0x4000 + 74, GetYMUMChecksumFunctionNode.class);
    private IntegerResultNode result = new IntegerResultNode(0);

    @Override
    public ResultNode getResult() {
        return result;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        result.serialize(buf);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        result.deserialize(buf);
    }

    @Override
    public GetYMUMChecksumFunctionNode clone() {
        GetYMUMChecksumFunctionNode obj = (GetYMUMChecksumFunctionNode)super.clone();
        if (result != null) {
            obj.result = (IntegerResultNode)result.clone();
        }
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("result", result);
    }

    @Override
    protected boolean equalsExpression(ExpressionNode obj) {
        return equals(result, ((GetYMUMChecksumFunctionNode)obj).result);
    }
}
