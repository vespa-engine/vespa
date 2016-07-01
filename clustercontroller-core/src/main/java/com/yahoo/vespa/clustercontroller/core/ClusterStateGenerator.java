// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;

/**
 * Pure functional cluster state generator which deterministically constructs a full
 * cluster state given the state of the content cluster, a set of cluster controller
 * configuration parameters and the current time.
 *
 * Cluster state version is _not_ set here; its incrementing must be handled by the
 * caller.
 */
public class ClusterStateGenerator {
    static class Params {
        public ContentCluster cluster;

        static Params fromOptions(FleetControllerOptions opts) {
            return new Params();
        }
    }

    private static ClusterState emptyClusterState() {
        try {
            return new ClusterState("");
        } catch (Exception e) {
            throw new RuntimeException(e); // Should never happen for empty state string
        }
    }

    private static NodeState baselineNodeState(final NodeInfo nodeInfo) {
        final NodeState reported = nodeInfo.getReportedState();
        final NodeState wanted = nodeInfo.getWantedState();

        NodeState baseline = reported.clone();

        if (reported.above(wanted)) {
            // Only copy state and description from Wanted state; this preserves auxiliary
            // information such as disk states and startup timestamp.
            baseline.setState(wanted.getState());
            baseline.setDescription(wanted.getDescription());
        }
        // Special case: since each node is published with a single state, if we let a Retired node
        // be published with Initializing, it'd start receiving feed and merges. Avoid this by
        // having it be in maintenance instead for the duration of the init period.
        if (reported.getState() == State.INITIALIZING && wanted.getState() == State.RETIRED) {
            baseline.setState(State.MAINTENANCE);
        }
        // XXX description etc etc
        return baseline;
    }

    public static ClusterState generatedStateFrom(final Params params) {
        final ContentCluster cluster = params.cluster;
        ClusterState workingState = emptyClusterState();
        // FIXME temporary only
        int availableNodes = 0;
        for (final NodeInfo nodeInfo : cluster.getNodeInfo()) {
            // FIXME temporary only
            final NodeState nodeState = baselineNodeState(nodeInfo);
            workingState.setNodeState(nodeInfo.getNode(), nodeState);
            if (nodeInfo.getReportedState().getState() != State.DOWN) { // FIXME
                ++availableNodes;
            }
        }

        if (availableNodes == 0) {
            workingState.setClusterState(State.DOWN); // FIXME not correct
        }

        return workingState;
    }

    /**
     * TODO must take the following into account:
     *  - DONE - cluster bootstrap with all nodes down
     *  - DONE - node (distr+storage) reported states
     *  - DONE - node (distr+storage) "worse" wanted states
     *  - DONE - wanted state (distr+storage) cannot make generated state "better"
     *  - DONE - storage maintenance wanted state overrides all other states
     *  - DONE - wanted state description carries over to generated state
     *  - DONE - reported disk state not overridden by wanted state
     *  - DONE - implicit wanted state retired through config is applied
     *  - DONE - retired node in init is set to maintenance to inhibit load+merges
     *  - WIP - max transition time (reported down -> implicit generated maintenance -> generated down)
     *  - max init progress time (reported init -> generated down)
     *  - max premature crashes (reported up/down cycle -> generated down)
     *  - node startup timestamp inclusion if not all distributors have observed timestamps
     *  - min node count (distributor, storage) in state up for cluster to be up
     *  - min node ratio (distributor, storage) in state up for cluster to be up
     *  - implicit group node availability (only down-edge has to be considered, huzzah!)
     *  - slobrok disconnect grace period (reported down -> generated up)
     *  - distribution bits inferred from storage nodes and config
     *  - generation of accurate edge events for state deltas <- this is a sneaky one
     *  - reported init progress (current code implies this causes new state versions; why should it do that?)
     *  - reverse init progress(?)
     *  - mark "listing buckets" stage as down, since node can't receive load yet at that point
     *  - interaction with ClusterStateView
     *
     * Features such as minimum time before/between cluster state broadcasts are handled outside
     * the state generator.
     * Cluster state version management is also handled outside the generator.
     *
     * TODO how to ensure timer updates know that states should be altered?
     */
}
