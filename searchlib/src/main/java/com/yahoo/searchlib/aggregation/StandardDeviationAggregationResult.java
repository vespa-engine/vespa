// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.FloatResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * @author bjorncs
 */
public class StandardDeviationAggregationResult extends AggregationResult  {
    public static final int classId = registerClass(0x4000 + 89, StandardDeviationAggregationResult.class, StandardDeviationAggregationResult::new);

    private long count;
    private double sum;
    private double sumOfSquared;

    /**
     * Constructor used for deserialization. Will be instantiated with a default sketch.
     */
    @SuppressWarnings("unused")
    public StandardDeviationAggregationResult() {
        this(0, 0.0, 0.0);
    }

    public StandardDeviationAggregationResult(long count, double sum, double sumOfSquared) {
        this.count = count;
        this.sum = sum;
        this.sumOfSquared = sumOfSquared;
    }

    public double getStandardDeviation() {
        if (count == 0) {
            return 0;
        } else {
            double variance = (sumOfSquared - sum * sum / count) / count;
            return Math.sqrt(variance);
        }
    }

    @Override
    public ResultNode getRank() {
        return new FloatResultNode(getStandardDeviation());
    }

    @Override
    protected void onMerge(AggregationResult obj) {
        StandardDeviationAggregationResult other = (StandardDeviationAggregationResult) obj;
        count += other.count;
        sum += other.sum;
        sumOfSquared += other.sumOfSquared;
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        StandardDeviationAggregationResult other = (StandardDeviationAggregationResult) obj;
        return count == other.count && sum == other.sum && sumOfSquared == other.sumOfSquared;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putLong(null, count);
        buf.putDouble(null, sum);
        buf.putDouble(null, sumOfSquared);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        count = buf.getLong(null);
        sum = buf.getDouble(null);
        sumOfSquared = buf.getDouble(null);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("count", count);
        visitor.visit("sum", sum);
        visitor.visit("sumOfSquared", sumOfSquared);
    }
}
