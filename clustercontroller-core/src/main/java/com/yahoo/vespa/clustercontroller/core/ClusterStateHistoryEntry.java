// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ClusterStateHistoryEntry {

    private final ClusterStateBundle state;
    private final long time;

    ClusterStateHistoryEntry(final ClusterStateBundle state, final long time) {
        this.state = state;
        this.time = time;
    }

    public ClusterState getBaselineState() {
        return state.getBaselineClusterState();
    }

    public Map<String, ClusterState> getDerivedBucketSpaceStates() {
        return state.getDerivedBucketSpaceStates().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().getClusterState()));
    }

    public long time() {
        return time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterStateHistoryEntry that = (ClusterStateHistoryEntry) o;
        return time == that.time &&
                Objects.equals(state, that.state);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, time);
    }

    // String representation only used for test expectation failures and debugging output.
    // Actual status page history entry rendering emits formatted date/time.
    public String toString() {
        return String.format("state '%s' at time %d", state, time);
    }

}
