// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.aggregation.hll.*;
import com.yahoo.vespa.objects.BufferSerializer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class ExpressionCountAggregationResultTest {

    @Test
    public void requireThatSketchesAreMerged() {
        ExpressionCountAggregationResult aggr1 = createAggregationWithSparseSketch(42);
        ExpressionCountAggregationResult aggr2 = createAggregationWithSparseSketch(1337);

        // Merge performs union of the underlying data of the sparse sketch.
        aggr1.onMerge(aggr2);

        SparseSketch sketch = (SparseSketch) aggr1.getSketch();
        SketchUtils.assertSparseSketchContains(sketch, 42, 1337);
    }

    @Test
    public void requireThatEstimateIsCorrect() {
        ExpressionCountAggregationResult aggr = createAggregationWithSparseSketch(42);
        assertTrue(aggr.getEstimatedUniqueCount() == 1);
    }

    @Test
    public void requireThatPostMergeUpdatesEstimate() {
        ExpressionCountAggregationResult aggr = createAggregationWithSparseSketch(1337);
        assertEquals(1, aggr.getEstimatedUniqueCount());
        // Merge performs union of the underlying data of the sparse sketch.
        aggr.onMerge(createAggregationWithSparseSketch(9001));
        assertEquals(2, aggr.getEstimatedUniqueCount());
    }

    @Test
    public void requireThatSerializationDeserializationMatchSparseSketch() {
        ExpressionCountAggregationResult from = createAggregationWithSparseSketch(42);
        ExpressionCountAggregationResult to = createAggregationWithSparseSketch(1337);
        testSerialization(from, to);
    }

    @Test
    public void requireThatSerializationDeserializationMatchNormalSketch() {
        ExpressionCountAggregationResult from = createAggregationWithNormalSketch(42);
        ExpressionCountAggregationResult to = createAggregationWithNormalSketch(1337);
        testSerialization(from, to);
    }

    private void testSerialization(ExpressionCountAggregationResult from, ExpressionCountAggregationResult to) {
        BufferSerializer buffer = new BufferSerializer();
        from.serialize(buffer);
        buffer.flip();
        to.deserialize(buffer);

        assertEquals(from.getSketch(), to.getSketch());
    }

    private static ExpressionCountAggregationResult createAggregationWithSparseSketch(int sketchValue) {
        SparseSketch initialSketch = SketchUtils.createSparseSketch(sketchValue);
        return new ExpressionCountAggregationResult(
                initialSketch,
                sketch -> ((SparseSketch) sketch).size()
        );
    }

    private static ExpressionCountAggregationResult createAggregationWithNormalSketch(int sketchValue) {
        NormalSketch initialSketch = SketchUtils.createNormalSketch(sketchValue);
        return new ExpressionCountAggregationResult(
                initialSketch,
                sketch -> 42
        );
    }

}
