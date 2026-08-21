// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.FloatResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.searchlib.expression.SingleResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is an aggregated result holding the value of the aggregating expression for the first hit in the group.
 *
 * The first hit is the one with the highest hit rank; ties keep the hit that was aggregated first. The rank of that
 * hit is part of the result, so that merging partial results from several nodes picks the same hit no matter what
 * order they merge in. Ties between equal ranks are resolved by merge order, and are unspecified.
 *
 * @author johsol
 */
public class FirstAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 183, FirstAggregationResult.class, FirstAggregationResult::new);
    private SingleResultNode first = new FloatResultNode(0.0);
    private double hitRank = Double.NEGATIVE_INFINITY;
    private boolean hasValue = false;

    /**
     * Constructs an empty result node.
     */
    public FirstAggregationResult() {}

    /**
     * Constructs an instance of this class with the given value, and no hit rank.
     *
     * @param first The value of the first hit.
     */
    public FirstAggregationResult(SingleResultNode first) {
        setFirst(first);
    }

    /**
     * Constructs an instance of this class with the given value and hit rank.
     *
     * @param first The value of the first hit.
     * @param hitRank The rank of the first hit.
     */
    public FirstAggregationResult(SingleResultNode first, double hitRank) {
        setFirst(first);
        setHitRank(hitRank);
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
     * Returns the rank of the hit the value was taken from.
     *
     * @return The hit rank.
     */
    public final double getHitRank() {
        return hitRank;
    }

    /**
     * Sets the rank of the hit the value was taken from.
     *
     * @param hitRank The hit rank to set.
     * @return This, to allow chaining.
     */
    public final FirstAggregationResult setHitRank(double hitRank) {
        this.hitRank = hitRank;
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
        buf.putByte(null, hasValue ? (byte)1 : (byte)0);
        buf.putDouble(null, hitRank);
        serializeOptional(buf, first);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        hasValue = buf.getByte(null) != 0;
        hitRank = buf.getDouble(null);
        first = (SingleResultNode)deserializeOptional(buf);
    }

    @Override
    protected void onMerge(AggregationResult result) {
        FirstAggregationResult rhs = (FirstAggregationResult)result;
        if (!rhs.hasValue || (hasValue && rhs.hitRank <= hitRank)) {
            return;
        }
        first = (SingleResultNode)rhs.first.clone();
        hitRank = rhs.hitRank;
        hasValue = true;
    }

    @Override
    public FirstAggregationResult clone() {
        FirstAggregationResult obj = (FirstAggregationResult)super.clone();
        if (first != null) {
            obj.first = (SingleResultNode)first.clone();
        }
        return obj;
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        FirstAggregationResult rhs = (FirstAggregationResult)obj;
        return hasValue == rhs.hasValue && hitRank == rhs.hitRank && equals(first, rhs.first);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("hasValue", hasValue);
        visitor.visit("hitRank", hitRank);
        visitor.visit("first", first);
    }
}
