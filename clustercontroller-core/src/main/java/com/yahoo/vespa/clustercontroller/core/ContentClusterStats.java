// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.*;

/**
 * Class for storing pending content node stats for all content nodes in the cluster.
 *
 * @author hakonhall
 */
public class ContentClusterStats implements Iterable<ContentNodeStats> {

    // Maps a content node index to the content node's stats.
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

    ContentNodeStats getContentNode(Integer index) {
        return mapToNodeStats.get(index);
    }

    int size() {
        return mapToNodeStats.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentClusterStats that = (ContentClusterStats) o;
        return Objects.equals(mapToNodeStats, that.mapToNodeStats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapToNodeStats);
    }

    @Override
    public String toString() {
        return String.format("{mapToNodeStats=[%s]}",
                Arrays.toString(mapToNodeStats.entrySet().toArray()));
    }
}
