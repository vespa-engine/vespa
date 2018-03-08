// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Class tracking whether we have changes in current and previous cluster stats.
 *
 * The cluster stats are considered changed if the current and previous stats differs in whether
 * they may have buckets pending in the 'global' bucket space. This signals that the ClusterStateBundle should be recomputed.
 */
public class ClusterStatsChangeTracker {

    private AggregatedClusterStats aggregatedStats;
    private AggregatedStatsMergePendingChecker checker;
    private boolean prevMayHaveMergesPending;

    public ClusterStatsChangeTracker(AggregatedClusterStats aggregatedStats) {
        setAggregatedStats(aggregatedStats);
        prevMayHaveMergesPending = false;
    }

    private void setAggregatedStats(AggregatedClusterStats aggregatedStats) {
        this.aggregatedStats = aggregatedStats;
        checker = new AggregatedStatsMergePendingChecker(this.aggregatedStats);
    }

    public void syncBucketsPendingFlag() {
        prevMayHaveMergesPending = checker.mayHaveMergesPendingInGlobalSpace();
    }

    public void updateAggregatedStats(AggregatedClusterStats newAggregatedStats) {
        syncBucketsPendingFlag();
        setAggregatedStats(newAggregatedStats);
    }

    public boolean statsHaveChanged() {
        if (!aggregatedStats.hasUpdatesFromAllDistributors()) {
            return false;
        }
        if (prevMayHaveMergesPending != checker.mayHaveMergesPendingInGlobalSpace()) {
            return true;
        }
        return false;
    }

}
