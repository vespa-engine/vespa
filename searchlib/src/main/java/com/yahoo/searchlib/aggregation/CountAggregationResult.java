// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.IntegerResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is an aggregated result holding the number of aggregated hits.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class CountAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 81, CountAggregationResult.class);
    private long count = 0;

    /** Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set. */
    public CountAggregationResult() {

    }

    /**
     * Constructs an instance of this class with given count value.
     *
     * @param count the initial number of hits
     */
    public CountAggregationResult(long count) {
        setCount(count);
    }

    /** Returns the number of aggregated hits. */
    public final long getCount() {
        return count;
    }

    /**
     * Sets the number of aggregated hits.
     *
     * @param count the count
     * @return this, to allow chaining
     */
    public final CountAggregationResult setCount(long count) {
        this.count = count;
        return this;
    }

    @Override
    public ResultNode getRank() {
        return new IntegerResultNode(count);
    }

    @Override
    protected void onMerge(AggregationResult result) {
        count += ((CountAggregationResult)result).count;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putLong(null, count);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        count = buf.getLong(null);
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        return count == ((CountAggregationResult)obj).count;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + (int)count;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("count", count);
    }

}
