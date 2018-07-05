// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * The node is a request to retrieve the namespace-specific content of a document id.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class GetDocIdNamespaceSpecificFunctionNode extends DocumentAccessorNode {

    public static final int classId = registerClass(0x4000 + 73, GetDocIdNamespaceSpecificFunctionNode.class);
    private ResultNode result = null;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public GetDocIdNamespaceSpecificFunctionNode() {
        super();
    }

    /**
     * Constructs an instance of this class with given result.
     *
     * @param result The result to assign to this.
     */
    public GetDocIdNamespaceSpecificFunctionNode(ResultNode result) {
        super();
        setResult(result);
    }

    /**
     * Sets the result of this function.
     *
     * @param result The result to set.
     * @return This, to allow chaining.
     */
    public GetDocIdNamespaceSpecificFunctionNode setResult(ResultNode result) {
        this.result = result;
        return this;
    }

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
        serializeOptional(buf, result);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        result = (ResultNode)deserializeOptional(buf);
    }

    @Override
    public GetDocIdNamespaceSpecificFunctionNode clone() {
        GetDocIdNamespaceSpecificFunctionNode obj = (GetDocIdNamespaceSpecificFunctionNode)super.clone();
        if (result != null) {
            obj.result = (ResultNode)result.clone();
        }
        return obj;
    }

    @Override
    protected boolean equalsExpression(ExpressionNode obj) {
        return equals(result, ((GetDocIdNamespaceSpecificFunctionNode)obj).result);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("result", result);
    }
}
