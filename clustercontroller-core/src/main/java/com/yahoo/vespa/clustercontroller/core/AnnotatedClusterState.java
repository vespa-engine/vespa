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

    public ClusterState getClusterState() {
        return clusterState;
    }

    public Map<Node, NodeStateReason> getNodeStateReasons() {
        return Collections.unmodifiableMap(nodeStateReasons);
    }

    public ClusterStateReason getClusterStateReason() {
        return clusterStateReason;
    }
}
