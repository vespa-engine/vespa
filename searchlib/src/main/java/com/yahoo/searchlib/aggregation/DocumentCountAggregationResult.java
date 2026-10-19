// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

/** Holds the number of documents contributing to a group. */
public class DocumentCountAggregationResult extends CountAggregationResult {

    public static final int classId = registerClass(0x4000 + 187, DocumentCountAggregationResult.class,
                                                   DocumentCountAggregationResult::new);

    @Override
    protected int onGetClassId() {
        return classId;
    }

}
