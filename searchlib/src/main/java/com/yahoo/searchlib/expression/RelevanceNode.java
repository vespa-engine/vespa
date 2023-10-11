// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This abstract expression node represents a function to execute.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class RelevanceNode extends ExpressionNode {

    public static final int classId = registerClass(0x4000 + 59, RelevanceNode.class, RelevanceNode::new);
    private FloatResultNode relevance = new FloatResultNode();

    public RelevanceNode() {}

    @Override
    public void onPrepare() {}

    @Override
    public boolean onExecute() {
        return true;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        relevance.serialize(buf);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        relevance.deserialize(buf);
    }

    @Override
    public RelevanceNode clone() {
        RelevanceNode obj = (RelevanceNode)super.clone();
        obj.relevance = (FloatResultNode)relevance.clone();
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("relevance", relevance);
    }

    @Override
    public ResultNode getResult() {
        return relevance;
    }

    @Override
    protected boolean equalsExpression(ExpressionNode obj) {
        return relevance.equals(((RelevanceNode)obj).relevance);
    }
}
