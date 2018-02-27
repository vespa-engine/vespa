// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Class tracking whether we have changes in buckets pending state in the 'global' bucket space.
 *
 * The state is considered changed if the previous and current cluster stats differs in whether
 * they may have buckets pending in the 'global' bucket space. This signals that the ClusterStateBundle should be recomputed.
 */
public class ClusterStatsBucketsPendingState {

    private ClusterStatsAggregator aggregator;
    private boolean prevMayHaveBucketsPending;

    public ClusterStatsBucketsPendingState(ClusterStatsAggregator aggregator) {
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

    public boolean stateHasChanged() {
        if (!aggregator.hasUpdatesFromAllDistributors()) {
            return false;
        }
        if (prevMayHaveBucketsPending != aggregator.mayHaveBucketsPendingInGlobalSpace()) {
            return true;
        }
        return false;
    }
}
