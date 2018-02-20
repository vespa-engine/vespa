// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.hostinfo.StorageNodeStatsBridge;

import java.text.ParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    private static Logger log = Logger.getLogger(ClusterStateView.class.getName());
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

    /**
     * Creates a new ClusterStateView which is set up with the same static view of the cluster state
     * (i.e. the ClusterState is a clone of this instance's ClusterState), while transient and dynamic
     * parts are cleared.
     */
    public ClusterStateView cloneForNewState() {
        ClusterState clonedClusterState = clusterState.clone();
        return new ClusterStateView(
                clonedClusterState,
                createNewAggregator(clonedClusterState));
    }

    public ClusterState getClusterState() { return clusterState; }

    public void handleUpdatedHostInfo(Map<Integer, String> hostnames, NodeInfo node, HostInfo hostInfo) {
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
            log.log(LogLevel.DEBUG, "Current state version is " + currentStateVersion +
                    ", while host info received from distributor " + node.getNodeIndex() +
                    " is " + hostVersion);
            return;
        }

        statsAggregator.updateForDistributor(node.getNodeIndex(),
                StorageNodeStatsBridge.generate(hostInfo.getDistributor()));
    }

    public String toString() {
        return clusterState.toString();
    }

}
