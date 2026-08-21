// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.ExpressionNode;
import com.yahoo.searchlib.expression.FloatResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.searchlib.expression.SingleResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is an aggregated result holding the value of the aggregating expression for the hit whose key expression
 * evaluated to the smallest value: argmin(key, value). argmax(key, value) is the same result with a negated key
 * expression.
 *
 * @author johsol
 */
public class ArgminAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 183, ArgminAggregationResult.class, ArgminAggregationResult::new);
    private ExpressionNode keyExpression = null;
    private SingleResultNode key = new FloatResultNode(0.0);
    private SingleResultNode value = new FloatResultNode(0.0);
    private boolean hasValue = false;

    /**
     * Constructs an empty result node.
     */
    public ArgminAggregationResult() {}

    /**
     * Constructs an instance of this class with the given key and value.
     */
    public ArgminAggregationResult(SingleResultNode key, SingleResultNode value) {
        setKey(key);
        setValue(value);
    }

    /**
     * Returns the key of the hit the value was taken from.
     */
    public final SingleResultNode getKey() {
        return key;
    }

    /**
     * Sets the key of the hit the value was taken from.
     */
    public final ArgminAggregationResult setKey(SingleResultNode key) {
        this.key = key;
        return this;
    }

    /**
     * Returns the value of the hit with the smallest key.
     */
    public final SingleResultNode getValue() {
        return value;
    }

    /**
     * Sets the value of the hit with the smallest key, marking this as holding a value.
     */
    public final ArgminAggregationResult setValue(SingleResultNode value) {
        this.value = value;
        this.hasValue = true;
        return this;
    }

    /**
     * Returns the expression producing the key to minimize.
     */
    public final ExpressionNode getKeyExpression() {
        return keyExpression;
    }

    /**
     * Sets the expression producing the key to minimize.
     */
    public final ArgminAggregationResult setKeyExpression(ExpressionNode keyExpression) {
        this.keyExpression = keyExpression;
        return this;
    }

    /**
     * Returns whether any hit has been aggregated into this.
     */
    public final boolean hasValue() {
        return hasValue;
    }

    @Override
    public ResultNode getRank() {
        return value;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        serializeOptional(buf, keyExpression);
        buf.putByte(null, hasValue ? (byte)1 : (byte)0);
        serializeOptional(buf, key);
        serializeOptional(buf, value);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        keyExpression = (ExpressionNode)deserializeOptional(buf);
        hasValue = buf.getByte(null) != 0;
        key = (SingleResultNode)deserializeOptional(buf);
        value = (SingleResultNode)deserializeOptional(buf);
    }

    @Override
    protected void onMerge(AggregationResult result) {
        ArgminAggregationResult rhs = (ArgminAggregationResult)result;
        if (!rhs.hasValue || (hasValue && rhs.key.compareTo(key) >= 0)) {
            return;
        }
        key = (SingleResultNode)rhs.key.clone();
        value = (SingleResultNode)rhs.value.clone();
        hasValue = true;
    }

    @Override
    public ArgminAggregationResult clone() {
        ArgminAggregationResult obj = (ArgminAggregationResult)super.clone();
        if (keyExpression != null) {
            obj.keyExpression = keyExpression.clone();
        }
        if (key != null) {
            obj.key = (SingleResultNode)key.clone();
        }
        if (value != null) {
            obj.value = (SingleResultNode)value.clone();
        }
        return obj;
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        ArgminAggregationResult rhs = (ArgminAggregationResult)obj;
        return hasValue == rhs.hasValue && equals(keyExpression, rhs.keyExpression) && equals(key, rhs.key)
                && equals(value, rhs.value);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("keyExpression", keyExpression);
        visitor.visit("hasValue", hasValue);
        visitor.visit("key", key);
        visitor.visit("value", value);
    }
}
