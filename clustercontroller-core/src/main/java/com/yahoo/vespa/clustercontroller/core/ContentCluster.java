// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeListener;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static com.yahoo.vdslib.state.NodeState.ORCHESTRATOR_RESERVED_DESCRIPTION;

public class ContentCluster {

    private static final int pollingFrequency = 5000;

    private final String clusterName;
    private final ClusterInfo clusterInfo = new ClusterInfo();
    private final Map<Node, Long> nodeStartTimestamps = new TreeMap<>();

    private int slobrokGenerationCount = 0;
    private Distribution distribution;

    private final int maxNumberOfGroupsAllowedToBeDown;

    public ContentCluster(String clusterName, Collection<ConfiguredNode> configuredNodes, Distribution distribution) {
        this(clusterName, configuredNodes, distribution, -1);
    }

    public ContentCluster(FleetControllerOptions options) {
        this(options.clusterName(), options.nodes(), options.storageDistribution(), options.maxNumberOfGroupsAllowedToBeDown());
    }

    ContentCluster(String clusterName,
                   Collection<ConfiguredNode> configuredNodes,
                   Distribution distribution,
                   int maxNumberOfGroupsAllowedToBeDown) {
        if (configuredNodes == null) throw new IllegalArgumentException("Nodes must be set");
        this.clusterName = clusterName;
        this.distribution = distribution;
        setNodes(configuredNodes, new NodeListener() {});
        this.maxNumberOfGroupsAllowedToBeDown = maxNumberOfGroupsAllowedToBeDown;
    }

    public Distribution getDistribution() { return distribution; }

    public void setDistribution(Distribution distribution) {
        this.distribution = distribution;
        for (NodeInfo info : clusterInfo.getAllNodeInfos()) {
            info.setGroup(distribution);
        }
    }

    /** Sets the configured nodes of this cluster */
    public final void setNodes(Collection<ConfiguredNode> configuredNodes, NodeListener nodeListener) {
        clusterInfo.setNodes(configuredNodes, this, distribution, nodeListener);
    }

    public void setStartTimestamp(Node n, long startTimestamp) {
        nodeStartTimestamps.put(n, startTimestamp);
    }

    public long getStartTimestamp(Node n) {
        Long value = nodeStartTimestamps.get(n);
        return (value == null ? 0 : value);
    }

    public Map<Node, Long> getStartTimestamps() {
        return nodeStartTimestamps;
    }

    public void clearStates() {
        for (NodeInfo info : clusterInfo.getAllNodeInfos()) {
            info.setReportedState(null, 0);
        }
    }

    public boolean allStatesReported() {
        return clusterInfo.allStatesReported();
    }

    public int getPollingFrequency() { return pollingFrequency; }

    /** Returns the configured nodes of this as a read-only map indexed on node index (distribution key) */
    public Map<Integer, ConfiguredNode> getConfiguredNodes() {
        return clusterInfo.getConfiguredNodes();
    }

    public Collection<NodeInfo> getNodeInfos() {
        return Collections.unmodifiableCollection(clusterInfo.getAllNodeInfos());
    }

    public ClusterInfo clusterInfo() { return clusterInfo; }

    public String getName() { return clusterName; }

    public NodeInfo getNodeInfo(Node node) { return clusterInfo.getNodeInfo(node); }

    public int maxNumberOfGroupsAllowedToBeDown() { return maxNumberOfGroupsAllowedToBeDown; }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ContentCluster(").append(clusterName).append(") {");
        for (NodeInfo node : clusterInfo.getAllNodeInfos()) {
            sb.append("\n  ").append(node);
        }
        sb.append("\n}");
        return sb.toString();
    }

    public int getSlobrokGenerationCount() { return slobrokGenerationCount; }

    public void setSlobrokGenerationCount(int count) { slobrokGenerationCount = count; }

    /**
     * Checks if a node can be upgraded
     *
     * @param node the node to be checked for upgrad
     * @param clusterState the current cluster state version
     * @param condition the upgrade condition
     * @param oldState the old/current wanted state
     * @param newState state wanted to be set
     * @param inMoratorium whether the CC is in moratorium
     */
    public NodeStateChangeChecker.Result calculateEffectOfNewState(
            Node node, ClusterState clusterState, SetUnitStateRequest.Condition condition,
            NodeState oldState, NodeState newState, boolean inMoratorium) {

        NodeStateChangeChecker nodeStateChangeChecker = new NodeStateChangeChecker(this, inMoratorium);
        return nodeStateChangeChecker.evaluateTransition(node, clusterState, condition, oldState, newState);
    }

    /** Returns the indices of the nodes that have been safely set to the given state by the Orchestrator (best guess). */
    public List<Integer> nodesSafelySetTo(State state) {
        return switch (state) {
            // Orchestrator's ALLOWED_TO_BE_DOWN or PERMANENTLY_DOWN, respectively
            case MAINTENANCE, DOWN ->
                    clusterInfo.getStorageNodeInfos().stream()
                               .filter(storageNodeInfo -> {
                                   NodeState userWantedState = storageNodeInfo.getUserWantedState();
                                   return userWantedState.getState() == state &&
                                           Objects.equals(userWantedState.getDescription(), ORCHESTRATOR_RESERVED_DESCRIPTION);
                               })
                               .map(NodeInfo::getNodeIndex)
                               .toList();
            default ->
                    // Note: There is no trace left if the Orchestrator sets the state to UP, so that's handled
                    // like any other state:
                    List.of();
        };
    }

    public boolean hasConfiguredNode(int index) {
        return clusterInfo.hasConfiguredNode(index);
    }

}
