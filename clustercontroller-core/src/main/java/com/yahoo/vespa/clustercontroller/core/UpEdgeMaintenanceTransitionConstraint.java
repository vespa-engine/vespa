// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;

/**
 * Simple implementation which considers a node eligible for being set to maintenance iff
 * it was down or in maintenance in the cluster state previously published. This avoids
 * taking down nodes with pending global merges if these have already passed through an
 * up-edge, but runs the risk of taking down a large set of nodes when the cluster
 * controller is restarted (due to not knowing the previously published state).
 *
 * Output from this checker must always be combined with other constraints to decide whether
 * a node should be in maintenance; used alone it would most certainly cause havoc.
 *
 * Considered sufficient for beta testing use cases.
 */
public class UpEdgeMaintenanceTransitionConstraint implements MaintenanceTransitionConstraint {

    private final ClusterState previouslyPublishedDerivedState;

    private UpEdgeMaintenanceTransitionConstraint(ClusterState previouslyPublishedDerivedState) {
        this.previouslyPublishedDerivedState = previouslyPublishedDerivedState;
    }

    public static UpEdgeMaintenanceTransitionConstraint forPreviouslyPublishedState(ClusterState state) {
        return new UpEdgeMaintenanceTransitionConstraint(state);
    }

    @Override
    public boolean maintenanceTransitionAllowed(int contentNodeIndex) {
        NodeState prevState = previouslyPublishedDerivedState.getNodeState(Node.ofStorage(contentNodeIndex));
        return prevState.getState().oneOf("dm");
    }
}
