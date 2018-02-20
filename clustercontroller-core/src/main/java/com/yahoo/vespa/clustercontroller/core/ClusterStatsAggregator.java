// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashMap;
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

    // Maps the distributor node index to a map of content node index to the
    // content node's stats.
    private final Map<Integer, ContentClusterStats> distributorToStats = new HashMap<>();

    // This is only needed as an optimization. Is just the sum of distributorToStats' ContentClusterStats.
    // Maps the content node index to the content node stats for that node.
    // This MUST be kept up-to-date with distributorToStats;
    private final ContentClusterStats aggregatedStats;

    ClusterStatsAggregator(Set<Integer> distributors, Set<Integer> storageNodes) {
        this.distributors = distributors;
        aggregatedStats = new ContentClusterStats(storageNodes);
    }

    ContentClusterStats getAggregatedStats() {
        return aggregatedStats;
    }

    /**
     * Update the aggregator with the newest available stats from a distributor.
     */
    void updateForDistributor(int distributorIndex, ContentClusterStats clusterStats) {
        if (!distributors.contains(distributorIndex)) {
            return;
        }
        addStatsFromDistributor(distributorIndex, clusterStats);
    }

    private void addStatsFromDistributor(int distributorIndex, ContentClusterStats clusterStats) {
        ContentClusterStats prevClusterStats = distributorToStats.put(distributorIndex, clusterStats);

        for (ContentNodeStats contentNode : aggregatedStats) {
            Integer nodeIndex = contentNode.getNodeIndex();

            ContentNodeStats statsToAdd = clusterStats.getContentNode(nodeIndex);
            if (statsToAdd != null) {
                contentNode.add(statsToAdd);
            }

            if (prevClusterStats != null) {
                ContentNodeStats statsToSubtract = prevClusterStats.getContentNode(nodeIndex);
                if (statsToSubtract != null) {
                    contentNode.subtract(statsToSubtract);
                }
            }
        }
    }

}
