// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Class tracking whether we have changes in current and previous cluster stats.
 *
 * The cluster stats are considered changed if the current and previous stats differs in whether
 * they may have buckets pending in the 'global' bucket space. This signals that the ClusterStateBundle should be recomputed.
 */
public class ClusterStatsChangeTracker {

    private ClusterStatsAggregator aggregator;
    private boolean prevMayHaveBucketsPending;

    public ClusterStatsChangeTracker(ClusterStatsAggregator aggregator) {
        this.aggregator = aggregator;
        this.prevMayHaveBucketsPending = false;
    }

    public void syncBucketsPendingFlag() {
        prevMayHaveBucketsPending = aggregator.mayHaveBucketsPendingInGlobalSpace();
    }

    public void updateAggregator(ClusterStatsAggregator newAggregator) {
        syncBucketsPendingFlag();
        aggregator = newAggregator;
    }

    public boolean statsHaveChanged() {
        if (!aggregator.hasUpdatesFromAllDistributors()) {
            return false;
        }
        if (prevMayHaveBucketsPending != aggregator.mayHaveBucketsPendingInGlobalSpace()) {
            return true;
        }
        return false;
    }
}
