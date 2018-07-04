// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.searchlib.expression.SingleResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is an aggregated result holding the sum of the aggregating expression for all matching hits.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class SumAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 82, SumAggregationResult.class);
    private SingleResultNode sum;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public SumAggregationResult() {

    }

    /**
     * Constructs an instance of this class with given sum.
     *
     * @param sum The initial sum to set.
     */
    public SumAggregationResult(SingleResultNode sum) {
        setSum(sum);
    }

    /**
     * Returns the sum of all results in this.
     *
     * @return The numeric sum.
     */
    public final SingleResultNode getSum() {
        return sum;
    }

    /**
     * Sets the sum of all results in this.
     *
     * @param sum The sum to set.
     * @return This, to allow chaining.
     */
    public final SumAggregationResult setSum(SingleResultNode sum) {
        this.sum = sum;
        return this;
    }

    @Override
    public ResultNode getRank() {
        return sum;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        serializeOptional(buf, sum);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        sum = (SingleResultNode)deserializeOptional(buf);
    }

    @Override
    protected void onMerge(AggregationResult result) {
        sum.add(((SumAggregationResult)result).sum);
    }

    @Override
    public SumAggregationResult clone() {
        SumAggregationResult obj = (SumAggregationResult)super.clone();
        if (sum != null) {
            obj.sum = (SingleResultNode)sum.clone();
        }
        return obj;
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        return equals(sum, ((SumAggregationResult)obj).sum);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("sum", sum);
    }
}
