// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.ExpressionNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * <p>This is the aggregation super-class from which all types of aggregation inherits.</p>
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public abstract class AggregationResult extends ExpressionNode {

    public static final int classId = registerClass(0x4000 + 80, AggregationResult.class);
    private ExpressionNode expression = null;
    private int tag = -1;

    /**
     * <p>Returns the tag of this aggregation result. This is useful for uniquely identifying a result.</p>
     *
     * @return The numerical tag.
     */
    public int getTag() {
        return tag;
    }

    /**
     * <p>Assigns a tag to this group.</p>
     *
     * @param tag The numerical tag to set.
     * @return This, to allow chaining.
     */
    public AggregationResult setTag(int tag) {
        this.tag = tag;
        return this;
    }

    /**
     * <p>This method is called when merging aggregation results. This method is simply a proxy for the abstract {@link
     * #onMerge(AggregationResult)} method.</p>
     *
     * @param result The result to merge with.
     */
    public void merge(AggregationResult result) {
        onMerge(result);
    }

    /**
     * <p>This method is called when all aggregation results have been merged. This method can be overloaded by
     * subclasses that need special behaviour to occur after merge.</p>
     */
    public void postMerge() {
        // empty
    }

    /**
     * <p>This method returns a value that can be used for ranking.</p>
     *
     * @return The rankable result.
     */
    public abstract ResultNode getRank();

    /**
     * <p>Sets the expression to aggregate on.</p>
     *
     * @param exp The expression.
     * @return This, to allow chaining.
     */
    public AggregationResult setExpression(ExpressionNode exp) {
        expression = exp;
        return this;
    }

    /**
     * <p>Returns the expression to aggregate on.</p>
     *
     * @return The expression.
     */
    public ExpressionNode getExpression() {
        return expression;
    }

    /**
     * <p>This method must be implemented by subclasses to support merge. It is called as the {@link
     * #merge(AggregationResult)} method is invoked.</p>
     *
     * @param result The result to merge with.
     */
    protected abstract void onMerge(AggregationResult result);

    @Override
    public ResultNode getResult() {
        return getRank();
    }

    @Override
    public void onPrepare() {

    }

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
        serializeOptional(buf, expression);
        buf.putInt(null, tag);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        expression = (ExpressionNode)deserializeOptional(buf);
        tag = buf.getInt(null);
    }

    @Override
    public AggregationResult clone() {
        AggregationResult obj = (AggregationResult)super.clone();
        if (expression != null) {
            obj.expression = expression.clone();
        }
        return obj;
    }

    @Override
    protected final boolean equalsExpression(ExpressionNode obj) {
        AggregationResult rhs = (AggregationResult)obj;
        if (!equals(expression, rhs.expression)) {
            return false;
        }
        if (tag != rhs.tag) {
            return false;
        }
        if (!equalsAggregation(rhs)) {
            return false;
        }
        return true;
    }

    protected abstract boolean equalsAggregation(AggregationResult obj);

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("expression", expression);
        visitor.visit("tag", tag);
    }
}
