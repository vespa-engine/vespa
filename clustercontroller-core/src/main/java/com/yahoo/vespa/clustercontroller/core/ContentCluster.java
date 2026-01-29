// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import static com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker.Result;

public final class ContentCluster {

    private static final int pollingFrequency = 5000;

    private final String clusterName;
    private final ClusterInfo clusterInfo = new ClusterInfo();
    private final Map<Node, Long> nodeStartTimestamps = new TreeMap<>();
    private final int maxNumberOfGroupsAllowedToBeDown;

    private int slobrokGenerationCount = 0;
    private Distribution distribution;
    // See `orchestrationGeneration()` below for semantics on this value
    private long orchestrationGeneration = 0;

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
        this.distribution = Objects.requireNonNull(distribution, "distribution must be non-null");
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
     * Returns a value that represents the current generation of this cluster controller
     * instance's orchestration decision "context" (distribution config, leadership, ...).
     *
     * Generations can be compared to ensure that decisions are not made based on arbitrary,
     * stale contexts. This is used to avoid ABA-style situations where the cluster controller
     * may otherwise make suboptimal orchestration decisions based on wanted states set when
     * the cluster had an incompatible orchestration configuration (e.g. flat vs grouped)
     *
     * This generation is monotonically increasing within the process' lifetime.
     *
     * Note that this is currently an entirely transient value, so a controller restart
     * or leadership reelection will invalidate the existing generation even if there
     * may have been no material changes to the underlying state of the cluster. This does
     * not affect correctness, but may cause the orchestration process to take more time
     * than necessary in situations where this happens.
     *
     * @return orchestration generation that only has useful semantics on this particular
     *         cluster controller instance for the lifetime of the process.
     */
    public long orchestrationGeneration() {
        return this.orchestrationGeneration;
    }

    /**
     * Indicate that future orchestration decisions can not look at existing nodes set into
     * Maintenance as "proof" that it's safe to set <em>further</em> nodes into Maintenance.
     * The existing node(s) will first have to come back up and allow the cluster to get
     * back into sync.
     */
    public void bumpOrchestrationGeneration() {
        this.orchestrationGeneration++;
    }

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
    public Result calculateEffectOfNewState(
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
