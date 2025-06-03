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
public record ContentClusterErrorStats(Map<Integer, ContentNodeErrorStats> contentNodeErrorStats) {

    private static Map<Integer, ContentNodeErrorStats> toEmptyErrorStats(Set<Integer> storageNodes) {
        Map<Integer, ContentNodeErrorStats> contentNodeErrorStats = new HashMap<>(storageNodes.size());
        for (Integer index : storageNodes) {
            contentNodeErrorStats.put(index, new ContentNodeErrorStats(index));
        }
        return contentNodeErrorStats;
    }

    public ContentClusterErrorStats(Set<Integer> storageNodes) {
        this(toEmptyErrorStats(storageNodes));
    }

    public ContentNodeErrorStats getNodeErrorStats(Integer index) {
        return contentNodeErrorStats.get(index);
    }

    public void clearAllStats() {
        contentNodeErrorStats.clear();
    }

}
