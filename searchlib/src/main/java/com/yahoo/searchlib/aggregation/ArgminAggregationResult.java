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
 * This is an aggregated result holding the value of the aggregating expression for the first hit in the group.
 *
 * Which hit is first is decided by an ordering key: with a key expression set, the first hit is the one with the
 * smallest key (negate the key to select by descending order); without one, the key is the negated hit rank, so the
 * first hit is the highest ranked one. The key of the winning hit is part of the result, so that merging partial
 * results from several nodes picks the same hit no matter what order they merge in. Ties keep the candidate seen
 * first, which is unspecified across nodes.
 *
 * @author johsol
 */
public class FirstAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 183, FirstAggregationResult.class, FirstAggregationResult::new);
    private ExpressionNode keyExpression = null;
    private SingleResultNode key = new FloatResultNode(0.0);
    private SingleResultNode first = new FloatResultNode(0.0);
    private boolean hasValue = false;

    /**
     * Constructs an empty result node.
     */
    public FirstAggregationResult() {}

    /**
     * Constructs an instance of this class with the given value, keyed on nothing.
     *
     * @param first The value of the first hit.
     */
    public FirstAggregationResult(SingleResultNode first) {
        setFirst(first);
    }

    /**
     * Constructs an instance of this class with the given value and ordering key.
     *
     * @param first The value of the first hit.
     * @param key The ordering key of the first hit.
     */
    public FirstAggregationResult(SingleResultNode first, SingleResultNode key) {
        setFirst(first);
        setKey(key);
    }

    /**
     * Returns the value of the first hit in this.
     *
     * @return The value of the first hit.
     */
    public final SingleResultNode getFirst() {
        return first;
    }

    /**
     * Sets the value of the first hit in this, marking this as holding a value.
     *
     * @param first The value to set.
     * @return This, to allow chaining.
     */
    public final FirstAggregationResult setFirst(SingleResultNode first) {
        this.first = first;
        this.hasValue = true;
        return this;
    }

    /**
     * Returns the ordering key of the hit the value was taken from.
     *
     * @return The ordering key.
     */
    public final SingleResultNode getKey() {
        return key;
    }

    /**
     * Sets the ordering key of the hit the value was taken from.
     *
     * @param key The ordering key to set.
     * @return This, to allow chaining.
     */
    public final FirstAggregationResult setKey(SingleResultNode key) {
        this.key = key;
        return this;
    }

    /**
     * Returns the expression producing the ordering key, or null when ordering by hit rank.
     *
     * @return The key expression, or null.
     */
    public final ExpressionNode getKeyExpression() {
        return keyExpression;
    }

    /**
     * Sets the expression producing the ordering key.
     *
     * @param keyExpression The key expression to set.
     * @return This, to allow chaining.
     */
    public final FirstAggregationResult setKeyExpression(ExpressionNode keyExpression) {
        this.keyExpression = keyExpression;
        return this;
    }

    /**
     * Returns whether any hit has been aggregated into this.
     *
     * @return True if this holds the value of a hit.
     */
    public final boolean hasValue() {
        return hasValue;
    }

    @Override
    public ResultNode getRank() {
        return first;
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
        serializeOptional(buf, first);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        keyExpression = (ExpressionNode)deserializeOptional(buf);
        hasValue = buf.getByte(null) != 0;
        key = (SingleResultNode)deserializeOptional(buf);
        first = (SingleResultNode)deserializeOptional(buf);
    }

    @Override
    protected void onMerge(AggregationResult result) {
        FirstAggregationResult rhs = (FirstAggregationResult)result;
        if (!rhs.hasValue || (hasValue && rhs.key.compareTo(key) >= 0)) {
            return;
        }
        first = (SingleResultNode)rhs.first.clone();
        key = (SingleResultNode)rhs.key.clone();
        hasValue = true;
    }

    @Override
    public FirstAggregationResult clone() {
        FirstAggregationResult obj = (FirstAggregationResult)super.clone();
        if (keyExpression != null) {
            obj.keyExpression = keyExpression.clone();
        }
        if (key != null) {
            obj.key = (SingleResultNode)key.clone();
        }
        if (first != null) {
            obj.first = (SingleResultNode)first.clone();
        }
        return obj;
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        FirstAggregationResult rhs = (FirstAggregationResult)obj;
        return hasValue == rhs.hasValue && equals(keyExpression, rhs.keyExpression) && equals(key, rhs.key)
                && equals(first, rhs.first);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("keyExpression", keyExpression);
        visitor.visit("hasValue", hasValue);
        visitor.visit("key", key);
        visitor.visit("first", first);
    }
}
