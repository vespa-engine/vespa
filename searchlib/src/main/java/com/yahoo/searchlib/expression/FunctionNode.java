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
public abstract class FunctionNode extends ExpressionNode {

    public static final int classId = registerClass(0x4000 + 42, FunctionNode.class);
    private ResultNode result = null;

    public FunctionNode setResult(ResultNode res) {
        this.result = res;
        return this;
    }

    @Override
    public final ResultNode getResult() {
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
    public FunctionNode clone() {
        FunctionNode obj = (FunctionNode)super.clone();
        if (result != null) {
            obj.result = (ResultNode)result.clone();
        }
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("result", result);
    }

    @Override
    protected final boolean equalsExpression(ExpressionNode obj) {
        FunctionNode rhs = (FunctionNode)obj;
        if (!equals(result, rhs.result)) {
            return false;
        }
        if (!equalsFunction(rhs)) {
            return false;
        }
        return true;
    }

    protected abstract boolean equalsFunction(FunctionNode obj);
}
