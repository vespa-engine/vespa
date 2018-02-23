// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Class checking whether a particular bucket space on a content node might have buckets pending.
 *
 * Aggregated stats over the entire content cluster is used to check this.
 */
public class AggregatedStatsMergePendingChecker implements MergePendingChecker {

    private final ContentClusterStats clusterStats;

    public AggregatedStatsMergePendingChecker(ContentClusterStats clusterStats) {
        this.clusterStats = clusterStats;
    }

    @Override
    public boolean hasMergesPending(String bucketSpace, int contentNodeIndex) {
        ContentNodeStats nodeStats = clusterStats.getContentNode(contentNodeIndex);
        if (nodeStats != null) {
            ContentNodeStats.BucketSpaceStats bucketSpaceStats = nodeStats.getBucketSpace(bucketSpace);
            if (bucketSpaceStats != null && bucketSpaceStats.mayHaveBucketsPending()) {
                return true;
            }
        }
        return false;
    }
}
