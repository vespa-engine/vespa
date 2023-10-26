// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

import com.yahoo.vespa.objects.BufferSerializer;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SparseSketchTest {

    @Test
    public void requireThatMergeDoesSetUnion() {
        SparseSketch s1 = new SparseSketch();
        s1.aggregate(42);
        s1.aggregate(9001);

        SparseSketch s2 = new SparseSketch();
        s2.aggregate(1337);
        s2.aggregate(9001);

        s1.merge(s2);

        HashSet<Integer> data = s1.data();
        assertEquals(3, s1.size());
        assertTrue(data.contains(42));
        assertTrue(data.contains(1337));
        assertTrue(data.contains(9001));
    }


    @Test
    public void requireThatSerializationRetainAllData() {
        SparseSketch from = new SparseSketch();
        from.aggregate(42);
        from.aggregate(1337);

        SparseSketch to = new SparseSketch();

        BufferSerializer buffer = new BufferSerializer();
        from.serialize(buffer);
        buffer.flip();
        to.deserialize(buffer);

        assertEquals(from, to);
    }

    @Test
    public void requireThatEqualsComparesDataContent() {
        SparseSketch s1 = new SparseSketch();
        s1.aggregate(1337);
        s1.aggregate(42);

        SparseSketch s2 = new SparseSketch();
        s2.aggregate(42);
        s2.aggregate(1337);

        assertEquals(s1.data(), s2.data());
    }
}
