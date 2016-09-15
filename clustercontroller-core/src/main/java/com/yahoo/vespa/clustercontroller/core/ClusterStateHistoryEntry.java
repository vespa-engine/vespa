// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;

public class ClusterStateHistoryEntry {

    private final ClusterState state;
    private final long time;

    ClusterStateHistoryEntry(final ClusterState state, final long time) {
        this.state = state;
        this.time = time;
    }

    public ClusterState state() {
        return state;
    }

    public long time() {
        return time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClusterStateHistoryEntry that = (ClusterStateHistoryEntry) o;

        if (time != that.time) return false;
        return state.equals(that.state);

    }

    @Override
    public int hashCode() {
        int result = state.hashCode();
        result = 31 * result + (int) (time ^ (time >>> 32));
        return result;
    }

    public String toString() {
        return String.format("state '%s' at time %d", state, time);
    }

}
