// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.aggregation.hll.HyperLogLogEstimator;
import com.yahoo.searchlib.aggregation.hll.NormalSketch;
import com.yahoo.searchlib.aggregation.hll.Sketch;
import com.yahoo.searchlib.aggregation.hll.SketchMerger;
import com.yahoo.searchlib.aggregation.hll.SparseSketch;
import com.yahoo.searchlib.aggregation.hll.UniqueCountEstimator;
import com.yahoo.searchlib.expression.IntegerResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is an aggregated result holding the number of unique documents matching a given expression.
 *
 * @author bjorncs
 */
public class ExpressionCountAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 88, ExpressionCountAggregationResult.class, ExpressionCountAggregationResult::new);
    private static final int UNDEFINED = -1;

    // The unique count estimator
    private final UniqueCountEstimator<Sketch<?>> estimator;
    // Sketch merger
    private final SketchMerger sketchMerger;
    // The sketch used as basis for the unique count calculation. The sketch is populated with data by the search nodes.
    private Sketch<?> sketch;
    // The estimated unique count. This value will not be serialized / deserialized.
    private long estimatedUniqueCount = UNDEFINED;


    /** Constructor used for deserialization. Will be instantiated with a default sketch. */
    public ExpressionCountAggregationResult() {
        this(new SparseSketch(), new HyperLogLogEstimator());
    }

    /**
     * Constructs an instance with a given sketch, sketch merger and unique count estimator. For test purposes.
     *
     * @param initialSketch the HLL sketch
     */
    public ExpressionCountAggregationResult(Sketch<?> initialSketch, UniqueCountEstimator<Sketch<?>> estimator) {
        this(initialSketch, estimator, new SketchMerger());
    }

    protected ExpressionCountAggregationResult(Sketch<?> initialSketch, UniqueCountEstimator<Sketch<?>> estimator,
                                               SketchMerger sketchMerger) {
        this.sketch = initialSketch;
        this.estimator = estimator;
        this.sketchMerger = sketchMerger;
    }

    /**
     * @return The unique count estimated by the HyperLogLog algorithm.
     */
    public long getEstimatedUniqueCount() {
        if (estimatedUniqueCount == UNDEFINED) {
            updateEstimate();
        }
        return estimatedUniqueCount;
    }

    @Override
    public ResultNode getRank() {
        return new IntegerResultNode(getEstimatedUniqueCount());
    }

    @Override
    protected void onMerge(AggregationResult result) {
        ExpressionCountAggregationResult other = (ExpressionCountAggregationResult) result;
        sketch = sketchMerger.merge(sketch, copySketch(other.sketch));
        // Any cached result should be invalidated.
        estimatedUniqueCount = UNDEFINED;
    }

    public Sketch<?> getSketch() {
        return sketch;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        sketch.serializeWithId(buf);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        sketch = (Sketch<?>) create(buf);
        estimatedUniqueCount = UNDEFINED;
    }

    @Override
    public ExpressionCountAggregationResult clone() {
        var copy = (ExpressionCountAggregationResult) super.clone();
        copy.sketch = copySketch(sketch);
        return copy;
    }

    private static Sketch<?> copySketch(Sketch<?> sketch) {
        if (sketch instanceof SparseSketch sparse) {
            var copy = new SparseSketch();
            copy.aggregate(sparse.data());
            return copy;
        }
        var normal = (NormalSketch) sketch;
        var copy = new NormalSketch(Integer.numberOfTrailingZeros(normal.size()));
        System.arraycopy(normal.data(), 0, copy.data(), 0, normal.size());
        return copy;
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        // obj is assumed to always be of correct type.
        ExpressionCountAggregationResult other = (ExpressionCountAggregationResult) obj;
        return sketch.equals(other.sketch);
    }

    private void updateEstimate() {
        estimatedUniqueCount = estimator.estimateCount(sketch);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("sketch", sketch);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + sketch.hashCode();
        return result;
    }
}
