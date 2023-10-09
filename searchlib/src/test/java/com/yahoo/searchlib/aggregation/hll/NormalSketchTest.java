// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

import com.yahoo.vespa.objects.BufferSerializer;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class NormalSketchTest {

    @Test
    public void requireThatSerializationIsCorrectForCompressibleData() {
        testSerializationForPrecision(16);
    }

    @Test
    public void requireThatSerializationIsCorrectForIncompressibleData() {
        // A sketch of precision 1 contains only two elements and will therefore not be compressible.
        testSerializationForPrecision(1);
    }

    private static void testSerializationForPrecision(int precision) {
        NormalSketch from = new NormalSketch(precision); // precision p => 2^p bytes
        for (int i = 0; i < from.size(); i++) {
            from.data()[i] = (byte) i;
        }
        NormalSketch to = new NormalSketch(precision);

        BufferSerializer buffer = new BufferSerializer();
        from.serialize(buffer);
        buffer.flip();
        to.deserialize(buffer);

        assertEquals(from, to);
    }

    @Test
    public void requireThatMergeDoesElementWiseMax() {
        NormalSketch s1 = new NormalSketch(2);
        setSketchValues(s1, 0, 1, 1, 3);
        NormalSketch s2 = new NormalSketch(2);
        setSketchValues(s2, 2, 1, 1, 0);
        s1.merge(s2);

        assertBucketEquals(s1, 0, 2);
        assertBucketEquals(s1, 1, 1);
        assertBucketEquals(s1, 2, 1);
        assertBucketEquals(s1, 3, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireThatMergingFailsForSketchesOfDifferentSize() {
        NormalSketch s1 = new NormalSketch(2);
        NormalSketch s2 = new NormalSketch(3);
        s1.merge(s2);
    }

    @Test
    public void requireThatEqualsIsCorrect() {
        NormalSketch s1 = new NormalSketch(1);
        setSketchValues(s1, 42, 127);
        NormalSketch s2 = new NormalSketch(1);
        setSketchValues(s2, 42, 127);
        assertEquals(s1, s2);
    }

    @Test
    public void requireThatSketchBucketsAreCorrectForSingleValues() {

        testSingleValueAggregation(0, 0, 23);
        testSingleValueAggregation(1, 1, 23);
        testSingleValueAggregation(-1, 1023, 1);
        testSingleValueAggregation(Integer.MAX_VALUE, 1023, 2);
        testSingleValueAggregation(Integer.MIN_VALUE, 0, 1);
        testSingleValueAggregation(42, 42, 23);
        testSingleValueAggregation(0b00000011_00000000_00000000_11000011, 0b11000011, 7);
    }

    private static void testSingleValueAggregation(int hashValue, int bucketIndex, int expectedValue) {
        NormalSketch sketch = new NormalSketch(10);
        sketch.aggregate(hashValue);
        assertBucketEquals(sketch, bucketIndex, expectedValue);
        for (int i = 0; i < sketch.size(); i++) {
            if (i == bucketIndex) {
                continue;
            }
            assertBucketEquals(sketch, i, 0);
        }
    }

    @Test
    public void requireThatSketchBucketsAreCorrectForMultipleValues() {
        NormalSketch sketch = new NormalSketch(10);

        // Aggregate multiple values
        sketch.aggregate(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        for (int i = 0; i < 10; i++) {
            assertBucketEquals(sketch, i, 23);
        }
        // Check that the other values are zero.
        for (int i = 10; i < 1024; i++) {
            assertBucketEquals(sketch, i, 0);
        }
    }

    private static void assertBucketEquals(NormalSketch sketch, int index, int expectedValue) {
        assertEquals(expectedValue, sketch.data()[index]);
    }

    private static void setSketchValues(NormalSketch sketch, Integer... values) {
        for (int i = 0; i < values.length; i++) {
            sketch.data()[i] = values[i].byteValue();
        }
    }

}
