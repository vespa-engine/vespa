// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.IntegerResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is an aggregated result holding the xor of the aggregating expression for all matching hits.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class XorAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 86, XorAggregationResult.class, XorAggregationResult::new);
    private long xor = 0;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public XorAggregationResult() {

    }

    /**
     * Constructs an instance of this class with given xor value.
     *
     * @param xor The initial xor value to set.
     */
    public XorAggregationResult(long xor) {
        setXor(xor);
    }

    /**
     * Returns the current xor value.
     *
     * @return The value.
     */
    public long getXor() {
        return xor;
    }

    /**
     * Sets the current xor value.
     *
     * @param xor The value to set.
     * @return This, to allow chaining.
     */
    public XorAggregationResult setXor(long xor) {
        this.xor = xor;
        return this;
    }

    @Override
    public ResultNode getRank() {
        return new IntegerResultNode(xor);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putLong(null, xor);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        xor = buf.getLong(null);
    }

    @Override
    protected void onMerge(AggregationResult result) {
        xor = xor ^ ((XorAggregationResult)result).xor;
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        return xor == ((XorAggregationResult)obj).xor;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + (int)xor;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("xor", xor);
    }
}
