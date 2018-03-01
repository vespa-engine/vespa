// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder used for testing only.
 */
public class ContentClusterStatsBuilder {

    private final Map<Integer, ContentNodeStatsBuilder> stats = new HashMap<>();

    public ContentClusterStatsBuilder add(int nodeIndex, String bucketSpace, long bucketsTotal, long bucketsPending) {
        return add(nodeIndex, bucketSpace, ContentNodeStats.BucketSpaceStats.of(bucketsTotal, bucketsPending));
    }

    public ContentClusterStatsBuilder addInvalid(int nodeIndex, String bucketSpace, long bucketsTotal, long bucketsPending) {
        return add(nodeIndex, bucketSpace, ContentNodeStats.BucketSpaceStats.invalid(bucketsTotal, bucketsPending));
    }

    public ContentClusterStatsBuilder add(int nodeIndex, String bucketSpace) {
        return add(nodeIndex, bucketSpace, ContentNodeStats.BucketSpaceStats.invalid());
    }

    public ContentClusterStatsBuilder add(int nodeIndex, String bucketSpace, ContentNodeStats.BucketSpaceStats bucketSpaceStats) {
        ContentNodeStatsBuilder nodeStatsBuilder = stats.get(nodeIndex);
        if (nodeStatsBuilder == null) {
            nodeStatsBuilder = ContentNodeStatsBuilder.forNode(nodeIndex);
            stats.put(nodeIndex, nodeStatsBuilder);
        }
        nodeStatsBuilder.add(bucketSpace, bucketSpaceStats);
        return this;
    }

    public ContentClusterStatsBuilder add(int nodeIndex) {
        stats.put(nodeIndex, ContentNodeStatsBuilder.forNode(nodeIndex));
        return this;
    }

    public ContentClusterStats build() {
        Map<Integer, ContentNodeStats> nodeToStatsMap = new HashMap<>();
        stats.forEach((nodeIndex, nodeStatsBuilder) ->
                nodeToStatsMap.put(nodeIndex, nodeStatsBuilder.build()));
        return new ContentClusterStats(nodeToStatsMap);
    }
}
