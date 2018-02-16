// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Class for storing the pending merge operation stats for all the content nodes.
 *
 * @author hakonhall
 */
public class ContentClusterStats implements Iterable<ContentNodeStats> {

    // Maps a storage node index to the storage node's pending merges stats.
    private final Map<Integer, ContentNodeStats> mapToNodeStats;

    public ContentClusterStats(Set<Integer> storageNodes) {
        mapToNodeStats = new HashMap<>(storageNodes.size());
        for (Integer index : storageNodes) {
            mapToNodeStats.put(index, new ContentNodeStats(index));
        }
    }

    public ContentClusterStats(Map<Integer, ContentNodeStats> mapToNodeStats) {
        this.mapToNodeStats = mapToNodeStats;
    }

    @Override
    public Iterator<ContentNodeStats> iterator() {
        return mapToNodeStats.values().iterator();
    }

    ContentNodeStats getStorageNode(Integer index) {
        return mapToNodeStats.get(index);
    }

    int size() {
        return mapToNodeStats.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentClusterStats)) {
            return false;
        }

        ContentClusterStats that = (ContentClusterStats) o;

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
