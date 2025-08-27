// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.data.JsonProducer;
import com.yahoo.searchlib.expression.FloatResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.memory.Memory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * This is an aggregated result holding the specified quantiles.
 *
 * @author johsol
 */
public class QuantileAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 179, QuantileAggregationResult.class, QuantileAggregationResult::new);

    private List<Double> quantiles;
    private KllDoublesSketch sketch;

    // Leave a byte to make it easier to change the sketch in the future.
    private byte extension;


    /**
     * Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set.
     */
    public QuantileAggregationResult() {
        this.quantiles = List.of();
        this.sketch = KllDoublesSketch.newHeapInstance();
    }

    /**
     * Constructs an instance of this class with given quantiles.
     */
    public QuantileAggregationResult(List<Double> quantiles) {
        validate(quantiles);
        this.quantiles = quantiles;
        this.sketch = KllDoublesSketch.newHeapInstance();
    }

    private static void validate(List<Double> in) {
        Objects.requireNonNull(in);

        if (in.isEmpty()) throw new IllegalArgumentException("quantiles must be non-empty");

        for (Double q : in) {
            if (q == null || Double.isNaN(q) || Double.isInfinite(q) || q < 0.0 || q > 1.0) {
                throw new IllegalArgumentException("quantile must be finite and in [0,1]: " + q);
            }
        }
    }

    public List<Double> getQuantiles() {
        return quantiles;
    }

    /**
     * Represents the result of this aggregator which is a list of pairs between
     * quantiles and values.
     *
     * @param entries
     */
    public record QuantileResult(List<Entry> entries) implements JsonProducer {

        @Override
        public StringBuilder writeJson(StringBuilder target) {
            target.append('[');
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                target.append("{\"quantile\":").append(entry.quantile).append(",\"value\":").append(entry.value).append('}');
                if (i < entries.size() - 1) {
                    target.append(',');
                }
            }
            target.append(']');
            return target;
        }

        @Override
        public String toJson() {
            return JsonProducer.super.toJson();
        }

        public record Entry(double quantile, double value) {
        }

        public static class Builder {
            private final List<Entry> entries = new ArrayList<>();

            public Builder add(double quantile, double value) {
                entries.add(new Entry(quantile, value));
                return this;
            }

            public QuantileResult build() {
                return new QuantileResult(List.copyOf(entries));
            }
        }
    }

    public QuantileResult getQuantileResults() {
        var builder = new QuantileResult.Builder();
        for (Double quantile : quantiles) {
            builder.add(quantile, sketch.getQuantile(quantile));
        }
        return builder.build();
    }

    public QuantileAggregationResult setQuantiles(List<Double> quantiles) {
        validate(quantiles);
        this.quantiles = quantiles;
        return this;
    }

    public QuantileAggregationResult updateSketch(double value) {
        this.sketch.update(value);
        return this;
    }

    @Override
    public ResultNode getRank() {
        return new FloatResultNode(0.0);
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
        buf.putInt(null, quantiles.size());
        for (var quantile : quantiles) {
            buf.putDouble(null, quantile);
        }

        buf.putByte(null, extension);

        byte[] bytes = this.sketch.toByteArray();
        buf.putInt(null, bytes.length);
        buf.put(null, bytes);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        {
            quantiles = new ArrayList<>();
            int length = buf.getInt(null);
            for (int i = 0; i < length; i++) {
                quantiles.add(buf.getDouble(null));
            }
        }

        extension = buf.getByte(null);

        {
            int length = buf.getInt(null);
            byte[] bytes = buf.getBytes(null, length);
            sketch = KllDoublesSketch.heapify(Memory.wrap(bytes));
        }
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        return quantiles.equals(((QuantileAggregationResult) obj).quantiles) && Arrays.equals(sketch.toByteArray(), ((QuantileAggregationResult) obj).sketch.toByteArray());
    }

    @Override
    public QuantileAggregationResult clone() {
        QuantileAggregationResult obj = (QuantileAggregationResult) super.clone();
        obj.quantiles = new ArrayList<>();
        obj.quantiles.addAll(quantiles);
        obj.sketch = KllDoublesSketch.heapify(Memory.wrap(sketch.toByteArray()));
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("quantiles", quantiles);
        for (var result : getQuantileResults().entries) {
            visitor.visit("quantile(" + result.quantile + ")", result.value);
        }
        visitor.visit("extension", extension);
    }
}
