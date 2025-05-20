// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class that stores content cluster stats (with bucket space stats per node) for
 * the current cluster state version.
 *
 * Each distributor reports bucket space stats for the different content nodes.
 * These reports arrive with getnodestate RPC calls,
 * and eventually ends up as calls to updateForDistributor().
 * No assumptions are made on the sequence of getnodestate calls.
 * For instance, it's perfectly fine for the calls to arrive in the
 * following order:
 *   distributor 0
 *   distributor 1
 *   distributor 1
 *   distributor 0
 *   distributor 2
 *   ... etc
 *
 * @author hakonhall
 */
public class ClusterStatsAggregator {

    private final Set<Integer> distributors;
    private final Set<Integer> nonUpdatedDistributors;

    // Maps the distributor node index to a map of content node index to the
    // content node's stats.
    private final Map<Integer, ContentClusterStats> distributorToStats = new HashMap<>();
    private final Map<Integer, ContentClusterErrorStats> distributorToErrorStats = new HashMap<>();

    // This is only needed as an optimization. Is just the sum of distributorToStats' ContentClusterStats.
    // Maps the content node index to the content node stats for that node.
    // This MUST be kept up-to-date with distributorToStats;
    private final ContentClusterStats aggregatedStats;
    // Aggregate of all error reports received from all distributors for all content nodes.
    // Makes it cheap to determine which distributors, if any, have observed errors towards
    // any given content node in the current cluster state. This conceptually mirrors
    // `aggregatedStats`, but with an extra internal indirection that is expected to be
    // very sparse in a healthy system.
    private final ContentClusterErrorStats aggregatedErrorStats;
    // This is the aggregate of aggregates across content nodes, allowing a reader to
    // get a O(1) view of all merges pending in the cluster.
    private final ContentNodeStats globallyAggregatedNodeStats = new ContentNodeStats(-1);

    private long documentCountTotal = 0;
    private long bytesTotal = 0;

    ClusterStatsAggregator(Set<Integer> distributors, Set<Integer> storageNodes) {
        this.distributors = distributors;
        nonUpdatedDistributors = new HashSet<>(distributors);
        aggregatedStats = new ContentClusterStats(storageNodes);
        aggregatedErrorStats = new ContentClusterErrorStats(storageNodes);
    }

    public AggregatedClusterStats getAggregatedStats() {
        return new AggregatedClusterStats() {

            @Override
            public boolean hasUpdatesFromAllDistributors() {
                return nonUpdatedDistributors.isEmpty();
            }

            @Override
            public ContentClusterStats getStats() {
                return aggregatedStats;
            }

            @Override
            public ContentClusterErrorStats getErrorStats() {
                return aggregatedErrorStats;
            }

            @Override
            public ContentNodeStats getGlobalStats() {
                return globallyAggregatedNodeStats;
            }
        };
    }

    public ContentNodeStats getAggregatedStatsForDistributor(int distributorIndex) {
        ContentNodeStats result = new ContentNodeStats(distributorIndex);
        ContentClusterStats distributorStats = distributorToStats.get(distributorIndex);
        if (distributorStats != null) {
            for (ContentNodeStats distributorStat : distributorStats) {
                result.add(distributorStat);
            }
        }
        return result;
    }

    public long getAggregatedDocumentCountTotal() {
        return documentCountTotal;
    }

    public long getAggregatedBytesTotal() {
        return bytesTotal;
    }

    MergePendingChecker createMergePendingChecker(double minMergeCompletionRatio) {
        return new AggregatedStatsMergePendingChecker(getAggregatedStats(), minMergeCompletionRatio);
    }

    /**
     * Update the aggregator with the newest available stats from a distributor.
     */
    void updateForDistributor(int distributorIndex, ContentClusterStats clusterStats) {
        if (!distributors.contains(distributorIndex)) {
            return;
        }
        nonUpdatedDistributors.remove(distributorIndex);
        addStatsFromDistributor(distributorIndex, clusterStats);
    }

    void updateErrorStatsFromDistributor(int distributorIndex, ContentClusterErrorStats updatedErrorStats) {
        if (!distributors.contains(distributorIndex)) {
            return;
        }
        // This does _not_ remove the distributor from `nonUpdatedDistributors` since this
        // method will be called even if the distributor has not yet converged to the newest
        // cluster state.
        // It also does not implicitly put an entry into the `distributorToStats` mapping; error
        // stats are kept entirely separate.
        ContentClusterErrorStats prevErrorStats = distributorToErrorStats.put(distributorIndex, updatedErrorStats);
        for (var contentNodeEntry : aggregatedErrorStats.getAllNodeErrorStats().entrySet()) {
            var nodeIndex   = contentNodeEntry.getKey();
            var contentNode = contentNodeEntry.getValue();
            ContentNodeErrorStats statsToAdd = updatedErrorStats.getNodeErrorStats(nodeIndex);
            if (statsToAdd != null) {
                contentNode.addErrorStatsFrom(distributorIndex, statsToAdd);
            }
            if (prevErrorStats != null) {
                ContentNodeErrorStats statsToSubtract = prevErrorStats.getNodeErrorStats(nodeIndex);
                if (statsToSubtract != null) {
                    contentNode.subtractErrorStatsFrom(distributorIndex, statsToSubtract);
                }
            }
        }
    }

    void clearAllErrorStatsFromDistributors() {
        distributorToErrorStats.clear();
        aggregatedErrorStats.clearAllStats();
    }

    private void addStatsFromDistributor(int distributorIndex, ContentClusterStats clusterStats) {
        ContentClusterStats prevClusterStats = distributorToStats.put(distributorIndex, clusterStats);

        if (prevClusterStats != null) {
            documentCountTotal -= prevClusterStats.getDocumentCountTotal();
            bytesTotal -= prevClusterStats.getBytesTotal();
        }
        documentCountTotal += clusterStats.getDocumentCountTotal();
        bytesTotal += clusterStats.getBytesTotal();

        for (ContentNodeStats contentNode : aggregatedStats) {
            Integer nodeIndex = contentNode.getNodeIndex();

            ContentNodeStats statsToAdd = clusterStats.getNodeStats(nodeIndex);
            if (statsToAdd != null) {
                contentNode.add(statsToAdd);
                globallyAggregatedNodeStats.add(statsToAdd);
            }

            if (prevClusterStats != null) {
                ContentNodeStats statsToSubtract = prevClusterStats.getNodeStats(nodeIndex);
                if (statsToSubtract != null) {
                    contentNode.subtract(statsToSubtract);
                    globallyAggregatedNodeStats.subtract(statsToSubtract);
                }
            }
        }
    }

}
