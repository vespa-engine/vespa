// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.hostinfo.StorageNodeStatsBridge;

import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Cluster Controller's view of the cluster given a particular state version. Some parts of the view
 * are static and only depend on the state version, e.g. which nodes are UP or DOWN. These static parts
 * are mostly represented by the ClusterState. The dynamic parts include stats for tracking outstanding
 * merges before steady-state is reached.
 *
 * @author hakonhall
 */
public class ClusterStateView {

    private static final Logger log = Logger.getLogger(ClusterStateView.class.getName());
    private final ClusterState clusterState;
    private final ClusterStatsAggregator statsAggregator;

    public static ClusterStateView create(String serializedClusterState) throws ParseException {
        ClusterState clusterState = new ClusterState(serializedClusterState);
        return new ClusterStateView(clusterState, createNewAggregator(clusterState));
    }

    public static ClusterStateView create(final ClusterState clusterState) {
        return new ClusterStateView(clusterState, createNewAggregator(clusterState));
    }

    private static ClusterStatsAggregator createNewAggregator(ClusterState clusterState) {
        Set<Integer> upDistributors = getIndicesOfUpNodes(clusterState, NodeType.DISTRIBUTOR);
        Set<Integer> upStorageNodes = getIndicesOfUpNodes(clusterState, NodeType.STORAGE);
        return new ClusterStatsAggregator(upDistributors, upStorageNodes);
    }

    ClusterStateView(ClusterState clusterState, ClusterStatsAggregator statsAggregator) {
        this.clusterState = clusterState;
        this.statsAggregator = statsAggregator;
    }

    /**
     * Returns the set of nodes that are up for a given node type. Non-private for testing.
     */
    static Set<Integer> getIndicesOfUpNodes(ClusterState clusterState, NodeType type) {
        int nodeCount = clusterState.getNodeCount(type);

        Set<Integer> nodesBeingUp = new HashSet<>();
        for (int i = 0; i < nodeCount; ++i) {
            Node node = new Node(type, i);
            NodeState nodeState = clusterState.getNodeState(node);
            State state = nodeState.getState();
            if (state == State.UP || state == State.INITIALIZING ||
                state == State.RETIRED || state == State.MAINTENANCE) {
                nodesBeingUp.add(i);
            }
        }

        return nodesBeingUp;
    }

    public ClusterState getClusterState() { return clusterState; }

    public void handleUpdatedHostInfo(NodeInfo node, HostInfo hostInfo) {
        if ( ! node.isDistributor()) return;

        final int hostVersion;
        if (hostInfo.getClusterStateVersionOrNull() == null) {
            // TODO: Consider logging a warning in the future (>5.36).
            // For now, a missing cluster state version probably means the content
            // node has not been updated yet.
            return;
        } else {
            hostVersion = hostInfo.getClusterStateVersionOrNull();
        }
        int currentStateVersion = clusterState.getVersion();

        if (hostVersion != currentStateVersion) {
            // The distributor may be old (null), or the distributor may not have updated
            // to the latest state version just yet. We log here with fine, because it may
            // also be a symptom of something wrong.
            log.log(Level.FINE, "Current state version is " + currentStateVersion +
                    ", while host info received from distributor " + node.getNodeIndex() +
                    " is " + hostVersion);
            return;
        }

        statsAggregator.updateForDistributor(node.getNodeIndex(),
                StorageNodeStatsBridge.generate(hostInfo.getDistributor()));
    }

    public ClusterStatsAggregator getStatsAggregator() {
        return statsAggregator;
    }

    public String toString() {
        return clusterState.toString();
    }

}
