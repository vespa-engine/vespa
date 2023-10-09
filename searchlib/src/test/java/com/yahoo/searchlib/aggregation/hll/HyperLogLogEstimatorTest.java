// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HyperLogLogEstimatorTest {

    private XXHash32 hashGenerator = XXHashFactory.safeInstance().hash32();

    @Test
    public void requireThatEstimateInRangeForSmallValueSetUsingNormalSketch() {
        testEstimateUsingNormalSketch(15, 1337);
    }

    @Test
    public void requireThatEstimateInRangeForLargeValueSetUsingNormalSketch() {
        testEstimateUsingNormalSketch(1_000_000, 1337);
    }

    @Test
    public void requireThatEstimateIsReasonableForFullNormalSketch() {
        HyperLogLogEstimator estimator = new HyperLogLogEstimator(10);
        NormalSketch sketch = new NormalSketch(10);
        // Fill sketch with 23 - highest possible zero prefix for precision 10.
        Arrays.fill(sketch.data(), (byte) 23);
        long estimate = estimator.estimateCount(sketch);
        assertTrue(estimate > 6_000_000_000l);
    }

    @Test
    public void requireThatEstimateIsCorrectForSparseSketch() {
        SparseSketch sketch = new SparseSketch();
        HyperLogLogEstimator estimator = new HyperLogLogEstimator(10);
        long estimate = estimator.estimateCount(sketch);
        assertEquals(0, estimate);

        // Check that estimate is correct for every possible sketch size up to threshold
        for (int i = 1; i <= HyperLogLog.SPARSE_SKETCH_CONVERSION_THRESHOLD; i++) {
            sketch.aggregate(i);
            estimate = estimator.estimateCount(sketch);
            assertEquals(i, estimate);
        }
    }

    private void testEstimateUsingNormalSketch(int nValues, int seed) {
        for (int precision = 4; precision <= 16; precision++) {
            HyperLogLogEstimator estimator = new HyperLogLogEstimator(precision);

            long uniqueCount = new Random(seed)
                    .ints(nValues)
                    .map(this::makeHash)
                    .distinct()
                    .count();

            Iterable<Integer> hashValues = () ->
                    new Random(seed)
                        .ints(nValues)
                        .map(this::makeHash)
                        .iterator();

            NormalSketch sketch = new NormalSketch(precision);
            sketch.aggregate(hashValues);
            long estimate = estimator.estimateCount(sketch);
            double standardError = standardErrorForPrecision(precision);
            assertTrue(estimate > uniqueCount * (1 - standardError) * 0.9);
            assertTrue(estimate < uniqueCount * (1 + standardError) * 1.1);
        }
    }

    private static double standardErrorForPrecision(int precision) {
        return 1.04 / Math.sqrt(1 << precision); // HLL standard error
    }


    private int makeHash(int value) {
        final int seed = 42424242;
        byte[] bytes = ByteBuffer.allocate(4).putInt(value).array();
        return hashGenerator.hash(bytes, 0, 4, seed);
    }
}
