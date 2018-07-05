// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.searchlib.expression.SingleResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is an aggregated result holding the minimum result of the matching hits.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class MinAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 84, MinAggregationResult.class);
    private SingleResultNode min;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public MinAggregationResult() {

    }

    /**
     * Constructs an instance of this class with given min value.
     *
     * @param min The initial minimum to set.
     */
    public MinAggregationResult(SingleResultNode min) {
        setMin(min);
    }

    /**
     * Returns the minimum value found in all matching hits.
     *
     * @return The value.
     */
    public final SingleResultNode getMin() {
        return min;
    }

    /**
     * Sets the minimum value found in all matching hits.
     *
     * @param min The value.
     * @return This, to allow chaining.
     */
    public final MinAggregationResult setMin(SingleResultNode min) {
        this.min = min;
        return this;
    }

    @Override
    public ResultNode getRank() {
        return min;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        serializeOptional(buf, min);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        min = (SingleResultNode)deserializeOptional(buf);
    }

    @Override
    protected void onMerge(AggregationResult result) {
        min.min(((MinAggregationResult)result).min);
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        return equals(min, ((MinAggregationResult)obj).min);
    }

    @Override
    public MinAggregationResult clone() {
        MinAggregationResult obj = (MinAggregationResult)super.clone();
        if (min != null) {
            obj.min = (SingleResultNode)min.clone();
        }
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("min", min);
    }
}
