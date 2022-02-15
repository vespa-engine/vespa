// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public class ConstantNode extends ExpressionNode {

    public static final int classId = registerClass(0x4000 + 49, ConstantNode.class);
    private ResultNode value = null;

    public ConstantNode() {}

    public ConstantNode(ResultNode value) {
        this.value = value;
    }

    public ResultNode getValue() {
        return value;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        serializeOptional(buf, value);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        value = (ResultNode)deserializeOptional(buf);
    }

    @Override
    public ConstantNode clone() {
        ConstantNode obj = (ConstantNode)super.clone();
        if (value != null) {
            obj.value = (ResultNode)value.clone();
        }
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("value", value);
    }

    @Override
    protected void onPrepare() {

    }

    @Override
    protected boolean onExecute() {
        return true;
    }

    @Override
    public ResultNode getResult() {
        return value;
    }

    @Override
    protected boolean equalsExpression(ExpressionNode obj) {
        return equals(value, ((ConstantNode)obj).value);
    }
}
