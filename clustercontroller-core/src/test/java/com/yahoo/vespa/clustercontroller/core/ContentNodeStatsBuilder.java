// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashMap;
import java.util.Map;

public class ContentNodeStatsBuilder {

    private final int nodeIndex;
    private final Map<String, ContentNodeStats.BucketSpaceStats> stats = new HashMap<>();

    private ContentNodeStatsBuilder(int nodeIndex) {
        this.nodeIndex = nodeIndex;
    }

    static ContentNodeStatsBuilder forNode(int nodeIndex) {
        return new ContentNodeStatsBuilder(nodeIndex);
    }

    public ContentNodeStatsBuilder add(String bucketSpace, long bucketsTotal, long bucketsPending) {
        return add(bucketSpace, ContentNodeStats.BucketSpaceStats.of(bucketsTotal, bucketsPending));
    }

    public ContentNodeStatsBuilder add(String bucketSpace, ContentNodeStats.BucketSpaceStats bucketSpaceStats) {
        stats.put(bucketSpace, bucketSpaceStats);
        return this;
    }

    ContentNodeStats build() {
        return new ContentNodeStats(nodeIndex, stats);
    }
}
