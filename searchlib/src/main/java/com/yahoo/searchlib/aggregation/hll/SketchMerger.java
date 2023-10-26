// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

/**
 * This class is responsible for merging any combinations of two {@link Sketch} instances.
 */
public class SketchMerger {

    /**
     * Merges one of the two sketches into the other. The merge operation is performed in-place is possible.
     *
     * @param left Either a {@link NormalSketch} or {@link SparseSketch}.
     * @param right Either a {@link NormalSketch} or {@link SparseSketch}.
     * @return The merged sketch. Is either first parameter, the other parameter or a new instance.
     */
    public Sketch<?> merge(Sketch<?> left, Sketch<?> right) {
        if (left instanceof NormalSketch && right instanceof NormalSketch) {
            return mergeNormalWithNormal(asNormal(left), asNormal(right));
        } else if (left instanceof NormalSketch && right instanceof SparseSketch) {
            return mergeNormalWithSparse(asNormal(left), asSparse(right));
        } else if (left instanceof SparseSketch && right instanceof NormalSketch) {
            return mergeNormalWithSparse(asNormal(right), asSparse(left));
        } else if (left instanceof SparseSketch && right instanceof SparseSketch) {
            return mergeSparseWithSparse(asSparse(left), asSparse(right));
        } else {
            throw new IllegalArgumentException(
                    String.format("Invalid sketch types: left=%s, right=%s", right.getClass(), left.getClass()));
        }
    }

    private Sketch<?> mergeSparseWithSparse(SparseSketch dest, SparseSketch other) {
        dest.merge(other);
        if (dest.size() > HyperLogLog.SPARSE_SKETCH_CONVERSION_THRESHOLD) {
            NormalSketch newSketch = new NormalSketch();
            newSketch.aggregate(dest.data());
            return newSketch;
        }
        return dest;
    }

    private NormalSketch mergeNormalWithNormal(NormalSketch dest, NormalSketch other) {
        dest.merge(other);
        return dest;
    }

    private NormalSketch mergeNormalWithSparse(NormalSketch dest, SparseSketch other) {
        NormalSketch newSketch = new NormalSketch();
        newSketch.aggregate(other.data());
        dest.merge(newSketch);
        return dest;
    }

    private static NormalSketch asNormal(Sketch<?> sketch) {
        return (NormalSketch) sketch;
    }

    private static SparseSketch asSparse(Sketch<?> sketch) {
        return (SparseSketch) sketch;
    }
}
