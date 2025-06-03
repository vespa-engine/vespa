// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Class for storing pending content node stats for all content nodes in the cluster.
 *
 * @author hakonhall
 */
public record ContentClusterStats(long documentCountTotal,
                                  long bytesTotal,
                                  // Maps a content node index to the content node's stats.
                                  Map<Integer, ContentNodeStats> mapToNodeStats)
        implements Iterable<ContentNodeStats> {

    private static Map<Integer, ContentNodeStats> toEmptyNodeStats(Set<Integer> storageNodes) {
        Map<Integer, ContentNodeStats> mapToNodeStats = new HashMap<>(storageNodes.size());
        for (Integer index : storageNodes) {
            mapToNodeStats.put(index, new ContentNodeStats(index));
        }
        return mapToNodeStats;
    }

    public ContentClusterStats(long documentCountTotal, long bytesTotal, Set<Integer> storageNodes) {
        this(documentCountTotal, bytesTotal, toEmptyNodeStats(storageNodes));
    }

    public ContentClusterStats(Set<Integer> storageNodes) {
        this(0, 0, storageNodes);
    }

    public ContentClusterStats(Map<Integer, ContentNodeStats> mapToNodeStats) {
        this(0, 0, mapToNodeStats);
    }

    @Override
    public Iterator<ContentNodeStats> iterator() {
        return mapToNodeStats.values().iterator();
    }

    public long getDocumentCountTotal() { return documentCountTotal; }
    public long getBytesTotal() { return bytesTotal; }

    public ContentNodeStats getNodeStats(Integer index) { return mapToNodeStats.get(index);}

    public int size() {
        return mapToNodeStats.size();
    }

}
