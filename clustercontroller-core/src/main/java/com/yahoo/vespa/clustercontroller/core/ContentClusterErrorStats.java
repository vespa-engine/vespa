// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stores, for each content node, the aggregate error reports from all distributors in
 * a content cluster, for a particular cluster state version (or attempt at reaching
 * a state version in the case the cluster does not converge). The per-node statistics
 * are sparse, so in a healthy cluster this is expected to be a lightweight mapping
 * from all nodes -> empty ContentNodeErrorStats instances.
 *
 * @author vekterli
 */
public class ContentClusterErrorStats {

    private final Map<Integer, ContentNodeErrorStats> contentNodeErrorStats;

    public ContentClusterErrorStats(Set<Integer> storageNodes) {
        contentNodeErrorStats = new HashMap<>(storageNodes.size());
        for (Integer index : storageNodes) {
            contentNodeErrorStats.put(index, new ContentNodeErrorStats(index));
        }
    }

    public ContentClusterErrorStats(Map<Integer, ContentNodeErrorStats> contentNodeErrorStats) {
        this.contentNodeErrorStats = contentNodeErrorStats;
    }

    public ContentNodeErrorStats getNodeErrorStats(Integer index) {
        return contentNodeErrorStats.get(index);
    }

    public Map<Integer, ContentNodeErrorStats> getAllNodeErrorStats() {
        return contentNodeErrorStats;
    }

    public void clearAllStats() {
        contentNodeErrorStats.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ContentClusterErrorStats that = (ContentClusterErrorStats) o;
        return Objects.equals(contentNodeErrorStats, that.contentNodeErrorStats);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(contentNodeErrorStats);
    }

    @Override
    public String toString() {
        return String.format("{contentNodeErrorStats=[%s]}", Arrays.toString(contentNodeErrorStats.entrySet().toArray()));
    }

}
