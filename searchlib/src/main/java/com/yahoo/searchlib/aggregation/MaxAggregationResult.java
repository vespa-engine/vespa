// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.searchlib.expression.SingleResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is an aggregated result holding the maximum result of the matching hits.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class MaxAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 83, MaxAggregationResult.class);
    private SingleResultNode max;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public MaxAggregationResult() {

    }

    /**
     * Constructs an instance of this class with given max value.
     *
     * @param max The initial maximum to set.
     */
    public MaxAggregationResult(SingleResultNode max) {
        setMax(max);
    }

    /**
     * Returns the maximum value found in all matching hits.
     *
     * @return The value.
     */
    public final SingleResultNode getMax() {
        return max;
    }

    /**
     * Sets the maximum value found in all matching hits.
     *
     * @param max The value.
     * @return This, to allow chaining.
     */
    public final MaxAggregationResult setMax(SingleResultNode max) {
        this.max = max;
        return this;
    }

    @Override
    public ResultNode getRank() {
        return max;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        serializeOptional(buf, max);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        max = (SingleResultNode)deserializeOptional(buf);
    }

    @Override
    protected void onMerge(AggregationResult result) {
        max.max(((MaxAggregationResult)result).max);
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        return equals(max, ((MaxAggregationResult)obj).max);
    }

    @Override
    public MaxAggregationResult clone() {
        MaxAggregationResult obj = (MaxAggregationResult)super.clone();
        if (max != null) {
            obj.max = (SingleResultNode)max.clone();
        }
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("max", max);
    }
}
