// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.aggregation.hll.HyperLogLogEstimator;
import com.yahoo.searchlib.aggregation.hll.SketchMerger;
import com.yahoo.searchlib.aggregation.hll.SparseSketch;

/**
 * Holds the distinct expression values counted by the content nodes.
 * Values are counted by their 32-bit hash. Up to {@link #SPARSE_LIMIT} distinct hashes are kept and
 * counted directly; above that the count is a HyperLogLog estimate. The content nodes use the same limit.
 */
public class UniqueCountAggregationResult extends ExpressionCountAggregationResult {

    /** The most hashes kept before switching to an estimate. Must match the content nodes. */
    public static final int SPARSE_LIMIT = 512;

    public static final int classId = registerClass(0x4000 + 188, UniqueCountAggregationResult.class,
                                                   UniqueCountAggregationResult::new);

    public UniqueCountAggregationResult() {
        super(new SparseSketch(), new HyperLogLogEstimator(), new SketchMerger(SPARSE_LIMIT));
    }

    /**
     * Returns whether the count is not an estimate, meaning the merged sketch still holds every hash.
     * Two different values with the same 32-bit hash are still counted once, so this is not a
     * guarantee that the count is exact.
     */
    public boolean isExact() {
        return getSketch() instanceof SparseSketch;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

}
