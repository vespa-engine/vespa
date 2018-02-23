// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class VdsCluster
 *
 * Represents a VDS cluster.
 */
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.*;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.distribution.Group;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.VdsClusterHtmlRendrer;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;

import java.util.*;

public class ContentCluster {

    private final String clusterName;

    private final ClusterInfo clusterInfo = new ClusterInfo();

    private final Map<Node, Long> nodeStartTimestamps = new TreeMap<>();

    private int slobrokGenerationCount = 0;

    private int pollingFrequency = 5000;

    private Distribution distribution;
    private int minStorageNodesUp;
    private double minRatioOfStorageNodesUp;

    public ContentCluster(String clusterName, Collection<ConfiguredNode> configuredNodes, Distribution distribution,
                          int minStorageNodesUp, double minRatioOfStorageNodesUp) {
        if (configuredNodes == null) throw new IllegalArgumentException("Nodes must be set");
        this.clusterName = clusterName;
        this.distribution = distribution;
        this.minStorageNodesUp = minStorageNodesUp;
        this.minRatioOfStorageNodesUp = minRatioOfStorageNodesUp;
        setNodes(configuredNodes);
    }

    public void writeHtmlState(
            final VdsClusterHtmlRendrer vdsClusterHtmlRendrer,
            final StringBuilder sb,
            final Timer timer,
            final ClusterState state,
            final Distribution distribution,
            final FleetControllerOptions options,
            final EventLog eventLog) {

        final VdsClusterHtmlRendrer.Table table =
                vdsClusterHtmlRendrer.createNewClusterHtmlTable(clusterName, slobrokGenerationCount);

        final List<Group> groups = LeafGroups.enumerateFrom(distribution.getRootGroup());

        for (int j=0; j<groups.size(); ++j) {
            final Group group = groups.get(j);
            assert(group != null);
            final String localName = group.getUnixStylePath();
            assert(localName != null);
            final TreeMap<Integer, NodeInfo> storageNodeInfoByIndex = new TreeMap<>();
            final TreeMap<Integer, NodeInfo> distributorNodeInfoByIndex = new TreeMap<>();
            for (ConfiguredNode configuredNode : group.getNodes()) {
                storeNodeInfo(configuredNode.index(), NodeType.STORAGE, storageNodeInfoByIndex);
                storeNodeInfo(configuredNode.index(), NodeType.DISTRIBUTOR, distributorNodeInfoByIndex);
            }
            table.renderNodes(
                    storageNodeInfoByIndex,
                    distributorNodeInfoByIndex,
                    timer,
                    state,
                    options.maxPrematureCrashes,
                    eventLog,
                    clusterName,
                    localName);
        }
        table.addTable(sb, options.stableStateTimePeriod);
    }

    private void storeNodeInfo(int nodeIndex, NodeType nodeType, Map<Integer, NodeInfo> nodeInfoByIndex) {
        NodeInfo nodeInfo = getNodeInfo(new Node(nodeType, nodeIndex));
        if (nodeInfo == null) return;
        nodeInfoByIndex.put(nodeIndex, nodeInfo);
    }

    public Distribution getDistribution() { return distribution; }

    public void setDistribution(Distribution distribution) {
        this.distribution = distribution;
        for (NodeInfo info : clusterInfo.getAllNodeInfo()) {
            info.setGroup(distribution);
        }
    }

    /** Sets the configured nodes of this cluster */
    public final void setNodes(Collection<ConfiguredNode> configuredNodes) {
        clusterInfo.setNodes(configuredNodes, this, distribution);
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
        for (NodeInfo info : clusterInfo.getAllNodeInfo()) {
            info.setReportedState(null, 0);
        }
    }

    public boolean allStatesReported() {
        return clusterInfo.allStatesReported();
    }

    public int getPollingFrequency() { return pollingFrequency; }
    public void setPollingFrequency(int millisecs) { pollingFrequency = millisecs; }

    /** Returns the configured nodes of this as a read-only map indexed on node index (distribution key) */
    public Map<Integer, ConfiguredNode> getConfiguredNodes() {
        return clusterInfo.getConfiguredNodes();
    }

    public Collection<NodeInfo> getNodeInfo() {
        return Collections.unmodifiableCollection(clusterInfo.getAllNodeInfo());
    }

    public ClusterInfo clusterInfo() { return clusterInfo; }

    public String getName() { return clusterName; }

    public NodeInfo getNodeInfo(Node node) { return clusterInfo.getNodeInfo(node); }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ContentCluster(").append(clusterName).append(") {");
        for (NodeInfo node : clusterInfo.getAllNodeInfo()) {
            sb.append("\n  ").append(node);
        }
        sb.append("\n}");
        return sb.toString();
    }

    public int getSlobrokGenerationCount() { return slobrokGenerationCount; }

    public void setSlobrokGenerationCount(int count) { slobrokGenerationCount = count; }

    private void getLeaves(Group node, List<Group> leaves, List<String> names, String name) {
        if (node.isLeafGroup()) {
            leaves.add(node);
            names.add(name + "/" + node.getName());
            return;
        }
        for (Group g : node.getSubgroups().values()) {
            getLeaves(g, leaves, names, name + (node.getName() != null ? "/" + node.getName() : ""));
        }
    }

    /**
     * Checks if a node can be upgraded
     *
     * @param node the node to be checked for upgrad
     * @param clusterState the current cluster state version
     * @param condition the upgrade condition
     * @param newState state wanted to be set  @return NodeUpgradePrechecker.Response
     */
    public NodeStateChangeChecker.Result calculateEffectOfNewState(
            Node node, ClusterState clusterState, SetUnitStateRequest.Condition condition, NodeState oldState, NodeState newState) {

        NodeStateChangeChecker nodeStateChangeChecker = new NodeStateChangeChecker(
                minStorageNodesUp,
                minRatioOfStorageNodesUp,
                distribution.getRedundancy(),
                clusterInfo);
        return nodeStateChangeChecker.evaluateTransition(node, clusterState, condition, oldState, newState);
    }

    public void setMinStorageNodesUp(int minStorageNodesUp) {
        this.minStorageNodesUp = minStorageNodesUp;
    }

    public void setMinRatioOfStorageNodesUp(double minRatioOfStorageNodesUp) {
        this.minRatioOfStorageNodesUp = minRatioOfStorageNodesUp;
    }

    public boolean hasConfiguredNode(int index) {
        return clusterInfo.hasConfiguredNode(index);
    }
}
