// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Tracks a configurable max number of cluster state history entries, pruning old entries
 * as newer entries are added. To save memory, state diffs are computed once and cached as
 * a string upon adding a new state bundle. Only the most recent bundle is retained in its
 * entirety.
 */
public class ClusterStateHistory {

    private final LinkedList<ClusterStateHistoryEntry> stateHistory = new LinkedList<>();
    private int maxHistoryEntryCount = 50;
    private ClusterStateBundle prevStateBundle = null;

    /**
     * Sets limit on how many cluster states can be kept in the in-memory queue. Once
     * the list exceeds this limit, the oldest state is repeatedly removed until the limit
     * is no longer exceeded.
     */
    void setMaxHistoryEntryCount(final int maxHistoryEntryCount) {
        this.maxHistoryEntryCount = maxHistoryEntryCount;
    }

    List<ClusterStateHistoryEntry> getHistory() {
        return Collections.unmodifiableList(stateHistory);
    }

    public void add(ClusterStateBundle currentClusterState, long currentTimeMs) {
        if (prevStateBundle != null) {
            stateHistory.addFirst(ClusterStateHistoryEntry.makeSuccessor(currentClusterState, prevStateBundle, currentTimeMs));
        } else {
            stateHistory.addFirst(ClusterStateHistoryEntry.makeFirstEntry(currentClusterState, currentTimeMs));
        }
        prevStateBundle = currentClusterState;
        while (stateHistory.size() > maxHistoryEntryCount) {
            stateHistory.removeLast();
        }
    }

}
