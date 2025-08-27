// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.FloatResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.memory.Memory;

import java.util.Arrays;

/**
 * This is an aggregated result holding the specified quantile.
 *
 * @author johsol
 */
public class QuantileAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 179, QuantileAggregationResult.class, QuantileAggregationResult::new);

    private double quantile;
    private KllDoublesSketch sketch;

    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public QuantileAggregationResult() {

    }

    /**
     * Constructs an instance of this class with given quantile.
     */
    public QuantileAggregationResult(double quantile) {
        this.quantile = quantile;
        this.sketch = KllDoublesSketch.newHeapInstance();
    }

    public double getQuantile() {
        return quantile;
    }

    public double getValue() {
        if (sketch.isEmpty()) {
            return 0;
        }
        return sketch.getQuantile(quantile);
    }

    public QuantileAggregationResult setQuantile(double quantile) {
        this.quantile = quantile;
        return this;
    }

    public QuantileAggregationResult updateSketch(double value) {
        this.sketch.update(value);
        return this;
    }

    @Override
    public ResultNode getRank() {
        return new FloatResultNode(sketch.getQuantile(quantile));
    }

    @Override
    protected void onMerge(AggregationResult result) {
        sketch.merge(((QuantileAggregationResult) result).sketch);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putDouble(null, quantile);
        byte[] bytes = this.sketch.toByteArray();

        buf.putInt(null, bytes.length);
        buf.put(null, bytes);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        quantile = buf.getDouble(null);
        int length = buf.getInt(null);
        byte[] bytes = buf.getBytes(null, length);
        sketch = KllDoublesSketch.heapify(Memory.wrap(bytes));
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        return (quantile == ((QuantileAggregationResult) obj).quantile) && Arrays.equals(sketch.toByteArray(), ((QuantileAggregationResult) obj).sketch.toByteArray());
    }

    @Override
    public QuantileAggregationResult clone() {
        QuantileAggregationResult obj = (QuantileAggregationResult) super.clone();
        obj.quantile = quantile;
        obj.sketch = KllDoublesSketch.heapify(Memory.wrap(sketch.toByteArray()));
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("quantile", quantile);
        visitor.visit("value", getValue());
    }
}
