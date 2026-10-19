// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.aggregation.hll.HyperLogLogEstimator;
import com.yahoo.searchlib.aggregation.hll.SketchMerger;
import com.yahoo.searchlib.aggregation.hll.SparseSketch;

/** Holds distinct nonempty expression values, retaining at least 1,000 hashes before estimation. */
public class UniqueCountAggregationResult extends ExpressionCountAggregationResult {

    public static final int classId = registerClass(0x4000 + 188, UniqueCountAggregationResult.class,
                                                   UniqueCountAggregationResult::new);

    public UniqueCountAggregationResult() {
        super(new SparseSketch(), new HyperLogLogEstimator(), new SketchMerger(1000));
    }

    /** Returns whether the final merged sketch retains individual value hashes. */
    public boolean isExact() {
        return getSketch() instanceof SparseSketch;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

}
