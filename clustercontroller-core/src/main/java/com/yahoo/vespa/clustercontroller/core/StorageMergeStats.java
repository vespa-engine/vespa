// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Class for storing the pending merge operation stats for all the storage nodes.
 *
 * @author hakonhall
 * @since 5.34
 */
public class StorageMergeStats implements Iterable<NodeMergeStats> {

    // Maps a storage node index to the storage node's pending merges stats.
    private final Map<Integer, NodeMergeStats> mapToNodeStats;

    public StorageMergeStats(Set<Integer> storageNodes) {
        mapToNodeStats = new HashMap<>(storageNodes.size());
        for (Integer index : storageNodes) {
            mapToNodeStats.put(index, new NodeMergeStats(index));
        }
    }

    public StorageMergeStats(Map<Integer, NodeMergeStats> mapToNodeStats) {
        this.mapToNodeStats = mapToNodeStats;
    }

    @Override
    public Iterator<NodeMergeStats> iterator() {
        return mapToNodeStats.values().iterator();
    }

    NodeMergeStats getStorageNode(Integer index) {
        return mapToNodeStats.get(index);
    }

    int size() {
        return mapToNodeStats.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StorageMergeStats)) {
            return false;
        }

        StorageMergeStats that = (StorageMergeStats) o;

        if (mapToNodeStats != null ? !mapToNodeStats.equals(that.mapToNodeStats) : that.mapToNodeStats != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return mapToNodeStats != null ? mapToNodeStats.hashCode() : 0;
    }

}
