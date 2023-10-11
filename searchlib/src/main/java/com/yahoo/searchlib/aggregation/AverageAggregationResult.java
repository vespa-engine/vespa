// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.IntegerResultNode;
import com.yahoo.searchlib.expression.NumericResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is an aggregated result holding the average of all results.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class AverageAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 85, AverageAggregationResult.class, AverageAggregationResult::new);
    private NumericResultNode sum;
    private long count;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public AverageAggregationResult() {

    }

    /**
     * Constructs an instance of this class with given sum and count values.
     *
     * @param sum   The initial sum to set.
     * @param count The initial number of results.
     */
    public AverageAggregationResult(NumericResultNode sum, long count) {
        setSum(sum);
        setCount(count);
    }

    /**
     * Returns the sum of all results in this.
     *
     * @return The numeric sum.
     */
    public final NumericResultNode getSum() {
        return sum;
    }

    /**
     * Sets the sum of all results in this.
     *
     * @param sum The sum to set.
     * @return This, to allow chaining.
     */
    public final AverageAggregationResult setSum(NumericResultNode sum) {
        this.sum = sum;
        return this;
    }

    /**
     * Returns the number of results in this.
     *
     * @return The number of results.
     */
    public final long getCount() {
        return count;
    }

    /**
     * Sets the number of results in this.
     *
     * @param count The number of results.
     * @return This, to allow chaining.
     */
    public final AverageAggregationResult setCount(long count) {
        this.count = count;
        return this;
    }

    /**
     * Returns the average value of the results. Because the result can be any numeric type, this method returns a
     * {@link NumericResultNode} object.
     *
     * @return The average result value.
     */
    public final NumericResultNode getAverage() {
        NumericResultNode sum = (NumericResultNode)this.sum.clone();
        if (count != 0) {
            sum.divide(new IntegerResultNode(count));
        }
        return sum;
    }

    @Override
    public ResultNode getRank() {
        return getAverage();
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putLong(null, count);
        serializeOptional(buf, sum);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        count = buf.getLong(null);
        sum = (NumericResultNode)deserializeOptional(buf);
    }

    @Override
    protected void onMerge(AggregationResult result) {
        sum.add(((AverageAggregationResult)result).sum);
        count += ((AverageAggregationResult)result).count;
    }

    @Override
    public AverageAggregationResult clone() {
        AverageAggregationResult obj = (AverageAggregationResult)super.clone();
        if (sum != null) {
            obj.sum = (NumericResultNode)sum.clone();
        }
        return obj;
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        AverageAggregationResult rhs = (AverageAggregationResult)obj;
        if (!equals(sum, rhs.sum)) {
            return false;
        }
        if (count != rhs.count) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + (int)count;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("sum", sum);
        visitor.visit("count", count);
    }
}
