// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Class checking whether a particular bucket space on a content node might have buckets pending.
 *
 * Aggregated stats over the entire content cluster is used to check this.
 */
public class AggregatedStatsMergePendingChecker implements MergePendingChecker {

    private final AggregatedClusterStats stats;
    private final double minMergeCompletionRatio;

    public AggregatedStatsMergePendingChecker(AggregatedClusterStats stats,
                                              double minMergeCompletionRatio) {
        this.stats = stats;
        this.minMergeCompletionRatio = minMergeCompletionRatio;
    }

    @Override
    public boolean mayHaveMergesPending(String bucketSpace, int contentNodeIndex) {
        if (!stats.hasUpdatesFromAllDistributors()) {
            return true;
        }
        ContentNodeStats nodeStats = stats.getStats().getContentNode(contentNodeIndex);
        if (nodeStats != null) {
            ContentNodeStats.BucketSpaceStats bucketSpaceStats = nodeStats.getBucketSpace(bucketSpace);
            return (bucketSpaceStats != null &&
                    bucketSpaceStats.mayHaveBucketsPending(minMergeCompletionRatio));
        }
        return true;
    }

}
