// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;

import java.util.*;

public class AnnotatedClusterState implements Cloneable {

    private final ClusterState clusterState;
    private final Map<Node, NodeStateReason> nodeStateReasons;
    private final Optional<ClusterStateReason> clusterStateReason;

    public static class Builder {
        private ClusterState clusterState = ClusterState.emptyState();
        private Optional<ClusterStateReason> clusterReason = Optional.empty();
        private Map<Node, NodeStateReason> nodeStateReasons = new HashMap<>();

        public Builder clusterState(String stateStr) {
            clusterState = ClusterState.stateFromString(stateStr);
            return this;
        }

        public Builder clusterReason(ClusterStateReason reason) {
            clusterReason = Optional.of(reason);
            return this;
        }

        public Builder storageNodeReason(int nodeIndex, NodeStateReason reason) {
            nodeStateReasons.put(Node.ofStorage(nodeIndex), reason);
            return this;
        }

        AnnotatedClusterState build() {
            return new AnnotatedClusterState(clusterState, clusterReason, nodeStateReasons);
        }
    }

    public AnnotatedClusterState(ClusterState clusterState,
                                 Optional<ClusterStateReason> clusterStateReason,
                                 Map<Node, NodeStateReason> nodeStateReasons)
    {
        this.clusterState = clusterState;
        this.clusterStateReason = clusterStateReason;
        this.nodeStateReasons = nodeStateReasons;
    }

    public static AnnotatedClusterState emptyState() {
        return new AnnotatedClusterState(ClusterState.emptyState(), Optional.empty(), emptyNodeStateReasons());
    }

    public static AnnotatedClusterState withoutAnnotations(ClusterState state) {
        return new AnnotatedClusterState(state, Optional.empty(), emptyNodeStateReasons());
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

    public Optional<ClusterStateReason> getClusterStateReason() {
        return clusterStateReason;
    }

    public AnnotatedClusterState clone() {
        return cloneWithClusterState(clusterState.clone());
    }

    public AnnotatedClusterState cloneWithClusterState(ClusterState newClusterState) {
        return new AnnotatedClusterState(newClusterState,
                getClusterStateReason(),
                getNodeStateReasons());
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
        return Objects.equals(clusterState, that.clusterState) &&
                Objects.equals(nodeStateReasons, that.nodeStateReasons) &&
                Objects.equals(clusterStateReason, that.clusterStateReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterState, nodeStateReasons, clusterStateReason);
    }

}
