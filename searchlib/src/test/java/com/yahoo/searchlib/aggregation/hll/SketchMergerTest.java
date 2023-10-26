// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SketchMergerTest {

    private final SketchMerger merger = new SketchMerger();

    @Test
    public void requireThatMergingTwoSmallSparseSketchesReturnsSparseSketch() {
        SparseSketch s1 = SketchUtils.createSparseSketch(1);
        SparseSketch s2 = SketchUtils.createSparseSketch(2);

        Sketch<?> result = merger.merge(s1, s2);
        assertEquals(result.getClass(), SparseSketch.class);
        assertTrue("Should return the instance given by first argument.", result == s1);
        SketchUtils.assertSketchContains(result, 1, 2);
    }

    @Test
    public void requireThatMergingTwoThresholdSizeSparseSketchesReturnsNormalSketch() {
        SparseSketch s1 = SketchUtils.createSparseSketch();
        SparseSketch s2 = SketchUtils.createSparseSketch();

        // Fill sketches with disjoint data.
        for (int i = 0; i < HyperLogLog.SPARSE_SKETCH_CONVERSION_THRESHOLD; i++) {
            s1.aggregate(i);
            s2.aggregate(i + HyperLogLog.SPARSE_SKETCH_CONVERSION_THRESHOLD);
        }

        Sketch<?> result = merger.merge(s1, s2);
        assertEquals(result.getClass(), NormalSketch.class);

        List<Integer> unionOfSketchData = new ArrayList<>();
        unionOfSketchData.addAll(s1.data());
        unionOfSketchData.addAll(s2.data());
        Integer[] expectedValues = unionOfSketchData.toArray(new Integer[unionOfSketchData.size()]);
        SketchUtils.assertSketchContains(result, expectedValues);
    }

    @Test
    public void requireThatMergingTwoNormalSketchesReturnsNormalSketch() {
        NormalSketch s1 = SketchUtils.createNormalSketch(1);
        NormalSketch s2 = SketchUtils.createNormalSketch(2);

        Sketch<?> result = merger.merge(s1, s2);
        assertEquals(result.getClass(), NormalSketch.class);
        assertTrue("Should return the instance given by first argument.", result == s1);
        SketchUtils.assertSketchContains(result, 1, 2);
    }

    @Test
    public void requireThatMergingNormalAndSparseSketchReturnsNormalSketch() {
        SparseSketch s1 = SketchUtils.createSparseSketch(1);
        NormalSketch s2 = SketchUtils.createNormalSketch(2);

        Sketch<?> result = merger.merge(s1, s2);
        assertEquals(result.getClass(), NormalSketch.class);
        assertTrue("Should return the NormalSketch instance given by the arguments.", result == s2);
        SketchUtils.assertSketchContains(result, 1, 2);
    }
}
