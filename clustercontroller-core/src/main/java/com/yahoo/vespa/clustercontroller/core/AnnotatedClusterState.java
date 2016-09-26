// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;

import java.util.Collections;
import java.util.Map;

public class AnnotatedClusterState {
    private final ClusterState clusterState;
    private final Map<Node, NodeStateReason> nodeStateReasons;
    private final ClusterStateReason clusterStateReason;

    public AnnotatedClusterState(ClusterState clusterState,
                                 ClusterStateReason clusterStateReason,
                                 Map<Node, NodeStateReason> nodeStateReasons)
    {
        this.clusterState = clusterState;
        this.clusterStateReason = clusterStateReason;
        this.nodeStateReasons = nodeStateReasons;
    }

    public static AnnotatedClusterState emptyState() {
        return new AnnotatedClusterState(ClusterStateUtil.emptyState(), null/*TODO*/, emptyNodeStateReasons());
    }

    static Map<Node, NodeStateReason> emptyNodeStateReasons() {
        return Collections.emptyMap();
    }

    public ClusterState getClusterState() {
        return clusterState;
    }

    public Map<Node, NodeStateReason> getNodeStateReasons() {
        return Collections.unmodifiableMap(nodeStateReasons);
    }

    public ClusterStateReason getClusterStateReason() {
        return clusterStateReason;
    }

    @Override
    public String toString() {
        return clusterState.toString();
    }

    public String toString(boolean verbose) {
        return clusterState.toString(verbose);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AnnotatedClusterState that = (AnnotatedClusterState) o;

        if (!clusterState.equals(that.clusterState)) return false;
        if (!nodeStateReasons.equals(that.nodeStateReasons)) return false;
        return clusterStateReason == that.clusterStateReason;

    }

    @Override
    public int hashCode() {
        int result = clusterState.hashCode();
        result = 31 * result + nodeStateReasons.hashCode();
        result = 31 * result + clusterStateReason.hashCode();
        return result;
    }
}
