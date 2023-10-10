// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Utility class for creating sketches and comparing their content.
 *
 * @author bjorncs
 */
public class SketchUtils {

    private SketchUtils() {}

    public static SparseSketch createSparseSketch(Integer... values) {
        SparseSketch sketch = new SparseSketch();
        sketch.aggregate(Arrays.asList(values));
        return sketch;
    }

    public static NormalSketch createNormalSketch(Integer... values) {
        NormalSketch sketch = new NormalSketch();
        sketch.aggregate(Arrays.asList(values));
        return sketch;
    }

    public static void assertSketchContains(Sketch<?> sketch, Integer... values) {
        if (sketch instanceof SparseSketch) {
            assertSparseSketchContains((SparseSketch) sketch, values);
        } else {
            assertNormalSketchContains((NormalSketch) sketch, values);
        }
    }

    public static void assertNormalSketchContains(NormalSketch sketch, Integer... values) {
        NormalSketch expectedSketch = createNormalSketch(values);
        assertEquals(expectedSketch, sketch);
    }

    public static void assertSparseSketchContains(SparseSketch sketch, Integer... values) {
        SparseSketch expectedSketch = createSparseSketch(values);
        assertEquals(expectedSketch, sketch);
    }
}
