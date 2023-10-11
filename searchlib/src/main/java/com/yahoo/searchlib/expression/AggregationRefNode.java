// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.searchlib.aggregation.AggregationResult;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This node holds the index of an ExpressionNode in an external array, and is used as a proxy in the back-end to allow
 * aggregators to be used in expressions.
 *
 * @author baldersheim
 */
public class AggregationRefNode extends ExpressionNode {

    public static final int classId = registerClass(0x4000 + 142, AggregationRefNode.class, AggregationRefNode::new);
    private AggregationResult result = null;
    private int index = - 1;

    public AggregationRefNode() { }

    public AggregationRefNode(int index) {
        this.index = index;
    }

    public AggregationRefNode(AggregationResult result) {
        this.result = result;
    }

    public AggregationResult getExpression() {
        return result;
    }

    public AggregationRefNode setExpression(AggregationResult result) {
        this.result = result;
        return this;
    }

    public AggregationRefNode setIndex(int index) {
        this.index = index;
        return this;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public boolean onExecute() {
        return result.execute();
    }

    @Override
    public void onPrepare() {
        result.prepare();
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putInt(null, index);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        index = buf.getInt(null);
        result = null;
    }

    @Override
    public AggregationRefNode clone() {
        AggregationRefNode obj = (AggregationRefNode)super.clone();
        obj.index = this.index;
        obj.result = this.result.clone();
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("index", index);
    }

    @Override
    public ResultNode getResult() {
        return result.getResult();
    }

    @Override
    public int hashCode() {
        return super.hashCode() + index;
    }

    @Override
    public boolean equalsExpression(ExpressionNode obj) {
        AggregationRefNode rhs = (AggregationRefNode)obj;
        if (index != rhs.index) {
            return false;
        }
        if (!equals(result, rhs.result)) {
            return false;
        }
        return true;
    }
}
