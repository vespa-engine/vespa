// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.document.FixedBucketSpaces;

import java.util.Iterator;

/**
 * Class checking whether a particular bucket space on a content node might have buckets pending.
 *
 * Aggregated stats over the entire content cluster is used to check this.
 */
public class AggregatedStatsMergePendingChecker implements MergePendingChecker {

    private final AggregatedClusterStats stats;

    public AggregatedStatsMergePendingChecker(AggregatedClusterStats stats) {
        this.stats = stats;
    }

    @Override
    public boolean mayHaveMergesPending(String bucketSpace, int contentNodeIndex) {
        if (!stats.hasUpdatesFromAllDistributors()) {
            return true;
        }
        ContentNodeStats nodeStats = stats.getStats().getContentNode(contentNodeIndex);
        if (nodeStats != null) {
            ContentNodeStats.BucketSpaceStats bucketSpaceStats = nodeStats.getBucketSpace(bucketSpace);
            if (bucketSpaceStats != null && bucketSpaceStats.mayHaveBucketsPending()) {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    public boolean mayHaveMergesPendingInGlobalSpace() {
        if (!stats.hasUpdatesFromAllDistributors()) {
            return true;
        }
        for (Iterator<ContentNodeStats> itr = stats.getStats().iterator(); itr.hasNext(); ) {
            ContentNodeStats stats = itr.next();
            if (mayHaveMergesPending(FixedBucketSpaces.globalSpace(), stats.getNodeIndex())) {
                return true;
            }
        }
        return false;
    }
}
