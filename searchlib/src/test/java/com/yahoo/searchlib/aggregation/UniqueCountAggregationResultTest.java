// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.aggregation.hll.SparseSketch;
import com.yahoo.searchlib.aggregation.hll.NormalSketch;
import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.Identifiable;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UniqueCountAggregationResultTest {

    private static final int LIMIT = UniqueCountAggregationResult.SPARSE_LIMIT;

    @Test
    public void hashesUpToTheSparseLimitAreCountedDirectlyAfterMerge() {
        var left = values(0, LIMIT / 2);
        left.merge(values(LIMIT / 2, LIMIT));
        assertTrue(left.isExact());
        assertEquals(LIMIT, left.getEstimatedUniqueCount());
        left.merge(values(0, LIMIT));
        assertTrue(left.isExact());
        assertEquals(LIMIT, left.getEstimatedUniqueCount());
    }

    @Test
    public void mergingPastTheSparseLimitReportsAnEstimateCloseToTheTrueCount() {
        var result = values(0, LIMIT);
        assertTrue(result.isExact());
        result.merge(values(LIMIT, LIMIT + 1));
        assertFalse(result.isExact());
        // Right after the switch the estimate uses linear counting, so it stays close to the true count.
        assertEquals(LIMIT + 1, result.getEstimatedUniqueCount(), 50);
    }

    @Test
    public void cloningAndMergingDoNotChangeTheSourceSketch() {
        var original = values(0, 1);
        assertEquals(1, original.getEstimatedUniqueCount());
        var copy = (UniqueCountAggregationResult) original.clone();
        copy.merge(values(1, 2));
        assertEquals(1, original.getEstimatedUniqueCount());
        assertEquals(2, copy.getEstimatedUniqueCount());

        var dense = values(0, LIMIT);
        dense.merge(values(LIMIT, LIMIT + 1));
        var denseBefore = dense.getSketch().toString();
        original.merge(dense);
        assertEquals(denseBefore, dense.getSketch().toString());
    }

    @Test
    public void serializationPreservesTheNewClassAndInvalidatesCachedEstimates() {
        var original = values(0, 3);
        var buffer = new BufferSerializer();
        original.serializeWithId(buffer);
        buffer.flip();
        var restored = (UniqueCountAggregationResult) Identifiable.create(buffer);
        assertEquals(UniqueCountAggregationResult.classId, restored.getClassId());
        assertTrue(restored.isExact());
        assertEquals(3, restored.getEstimatedUniqueCount());

        var replacement = values(0, 7);
        var replacementBuffer = new BufferSerializer();
        replacement.serialize(replacementBuffer);
        replacementBuffer.flip();
        restored.deserialize(replacementBuffer);
        assertEquals(7, restored.getEstimatedUniqueCount());
    }

    @Test
    public void denseEstimatesMatchTheNativeGroupingOrderingFixtures() {
        int[][] cases = {
            { 512, 1, 1, 710 },
            { 700, 1, 1, 688 },
            { 900, 2, 2, 2056 },
            { 1024, 2, 2, 2918 },
            { 1024, 1, 3, 2278 },
            { 1024, 3, 3, 5903 },
            { 1024, 7, 7, 94443 },
        };
        var result = new UniqueCountAggregationResult();
        for (var test : cases) {
            var sketch = new NormalSketch();
            for (int i = 0; i < test[0]; ++i) sketch.data()[i] = (byte) (i % 2 == 0 ? test[1] : test[2]);
            var buffer = new BufferSerializer();
            buffer.putByte(null, (byte) 0);
            buffer.putInt(null, -1);
            sketch.serializeWithId(buffer);
            buffer.flip();
            result.deserialize(buffer);
            assertFalse(result.isExact());
            assertEquals(test[3], result.getEstimatedUniqueCount());
        }
    }

    private static UniqueCountAggregationResult values(int from, int to) {
        var result = new UniqueCountAggregationResult();
        var sketch = (SparseSketch) result.getSketch();
        var random = new Random(20261013);
        for (int i = 0; i < to; ++i) {
            int hash = random.nextInt();
            if (i >= from) sketch.aggregate(hash);
        }
        return result;
    }

}
