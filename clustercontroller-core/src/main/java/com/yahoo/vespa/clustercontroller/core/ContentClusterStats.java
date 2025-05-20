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
public class ContentClusterStats implements Iterable<ContentNodeStats> {

    private final long documentCountTotal;
    private final long bytesTotal;
    // Maps a content node index to the content node's stats.
    private final Map<Integer, ContentNodeStats> mapToNodeStats;

    public ContentClusterStats(long documentCountTotal, long bytesTotal, Set<Integer> storageNodes) {
        this.documentCountTotal = documentCountTotal;
        this.bytesTotal = bytesTotal;
        mapToNodeStats = new HashMap<>(storageNodes.size());
        for (Integer index : storageNodes) {
            mapToNodeStats.put(index, new ContentNodeStats(index));
        }
    }

    public ContentClusterStats(Set<Integer> storageNodes) {
        this(0, 0, storageNodes);
    }

    public ContentClusterStats(long documentCountTotal, long bytesTotal, Map<Integer, ContentNodeStats> mapToNodeStats) {
        this.documentCountTotal = documentCountTotal;
        this.bytesTotal = bytesTotal;
        this.mapToNodeStats = mapToNodeStats;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentClusterStats that = (ContentClusterStats) o;
        return documentCountTotal == that.documentCountTotal &&
                bytesTotal == that.bytesTotal &&
                Objects.equals(mapToNodeStats, that.mapToNodeStats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentCountTotal, bytesTotal, mapToNodeStats);
    }

    @Override
    public String toString() {
        return String.format("{mapToNodeStats=[%s]}", Arrays.toString(mapToNodeStats.entrySet().toArray()));
    }
}
