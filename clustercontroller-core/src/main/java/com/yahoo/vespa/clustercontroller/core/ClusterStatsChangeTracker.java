// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.document.FixedBucketSpaces;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Class tracking whether we have changes in current and previous aggregated cluster stats.
 *
 * The cluster stats are considered changed if the current and previous stats differs in whether
 * they may have buckets pending in the 'global' bucket space. This signals that the ClusterStateBundle should be recomputed.
 */
public class ClusterStatsChangeTracker {

    private AggregatedClusterStats aggregatedStats;
    private AggregatedStatsMergePendingChecker checker;
    private Map<Integer, Boolean> prevMayHaveMergesPending = null;

    public ClusterStatsChangeTracker(AggregatedClusterStats aggregatedStats) {
        setAggregatedStats(aggregatedStats);
    }

    private void setAggregatedStats(AggregatedClusterStats aggregatedStats) {
        this.aggregatedStats = aggregatedStats;
        checker = new AggregatedStatsMergePendingChecker(this.aggregatedStats);
    }

    public void syncAggregatedStats() {
        prevMayHaveMergesPending = new HashMap<>();
        for (Iterator<ContentNodeStats> itr = aggregatedStats.getStats().iterator(); itr.hasNext(); ) {
            int nodeIndex = itr.next().getNodeIndex();
            prevMayHaveMergesPending.put(nodeIndex, mayHaveMergesPendingInGlobalSpace(nodeIndex));
        }
    }

    public void updateAggregatedStats(AggregatedClusterStats newAggregatedStats) {
        syncAggregatedStats();
        setAggregatedStats(newAggregatedStats);
    }

    public boolean statsHaveChanged() {
        if (!aggregatedStats.hasUpdatesFromAllDistributors()) {
            return false;
        }
        for (Iterator<ContentNodeStats> itr = aggregatedStats.getStats().iterator(); itr.hasNext(); ) {
            int nodeIndex = itr.next().getNodeIndex();
            boolean currValue = mayHaveMergesPendingInGlobalSpace(nodeIndex);
            Boolean prevValue = prevMayHaveMergesPendingInGlobalSpace(nodeIndex);
            if (prevValue != null) {
                if (prevValue != currValue) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean mayHaveMergesPendingInGlobalSpace(int nodeIndex) {
        return checker.mayHaveMergesPending(FixedBucketSpaces.globalSpace(), nodeIndex);
    }

    private Boolean prevMayHaveMergesPendingInGlobalSpace(int nodeIndex) {
        if (prevMayHaveMergesPending != null) {
            return prevMayHaveMergesPending.get(nodeIndex);
        }
        return null;
    }

}
