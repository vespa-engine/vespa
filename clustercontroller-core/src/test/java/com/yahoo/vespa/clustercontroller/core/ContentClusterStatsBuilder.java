// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder used for testing only.
 */
public class ContentClusterStatsBuilder {

    private final Map<Integer, Map<String, ContentNodeStats.BucketSpaceStats>> stats = new HashMap<>();

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
        Map<String, ContentNodeStats.BucketSpaceStats> contentNodeStats = stats.get(nodeIndex);
        if (contentNodeStats == null) {
            contentNodeStats = new HashMap<>();
            stats.put(nodeIndex, contentNodeStats);
        }
        contentNodeStats.put(bucketSpace, bucketSpaceStats);
        return this;
    }

    public ContentClusterStatsBuilder add(int nodeIndex) {
        stats.put(nodeIndex, new HashMap<>());
        return this;
    }

    public ContentClusterStats build() {
        Map<Integer, ContentNodeStats> nodeToStatsMap = new HashMap<>();
        stats.forEach((nodeIndex, bucketSpaces) ->
                nodeToStatsMap.put(nodeIndex, new ContentNodeStats(nodeIndex, bucketSpaces)));
        return new ContentClusterStats(nodeToStatsMap);
    }
}
