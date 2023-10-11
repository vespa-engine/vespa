// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Serializer;

import java.util.HashSet;

public class SparseSketch extends Sketch<SparseSketch> {

    public static final int classId = registerClass(0x4000 + 171, SparseSketch.class, SparseSketch::new);
    private final HashSet<Integer> values = new HashSet<>();

    @Override
    public void merge(SparseSketch other) {
        values.addAll(other.values);
    }

    /**
     * Aggregates the hash values.
     *
     * @param hashValues Provides an iterator for the hash values
     */
    @Override
    public void aggregate(Iterable<Integer> hashValues) {
        for (int hash: hashValues) {
            aggregate(hash);
        }
    }

    /**
     * Aggregates the hash value.
     *
     * @param hash Hash value.
     */
    @Override
    public void aggregate(int hash) {
        values.add(hash);
    }

    /**
     * Serializes the Sketch.
     *
     * Serialization format
     * ==================
     * Number of elements:      4 bytes
     * Elements:            N * 4 bytes
     * @param buf Serializer
     */
    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putInt(null, values.size());
        for (int value : values) {
            buf.putInt(null, value);
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        values.clear();
        int nElements = buf.getInt(null);
        for (int i = 0; i < nElements; i++) {
            values.add(buf.getInt(null));
        }
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    public HashSet<Integer> data() {
        return values;
    }

    public int size() {
        return values.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SparseSketch sketch = (SparseSketch) o;

        if (!values.equals(sketch.values)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "SparseSketch{" +
                "values=" + values +
                '}';
    }
}
