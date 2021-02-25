// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.mockito.Mockito.mock;

public class ClusterFixture {

    public final ContentCluster cluster;
    public final Distribution distribution;
    public final FakeTimer timer;
    private final EventLogInterface eventLog;
    final StateChangeHandler nodeStateChangeHandler;
    private final ClusterStateGenerator.Params params = new ClusterStateGenerator.Params();

    public ClusterFixture(ContentCluster cluster, Distribution distribution) {
        this.cluster = cluster;
        this.distribution = distribution;
        this.timer = new FakeTimer();
        this.eventLog = mock(EventLogInterface.class);
        this.nodeStateChangeHandler = createNodeStateChangeHandlerForCluster();
        this.params.cluster(this.cluster);
    }

    private StateChangeHandler createNodeStateChangeHandlerForCluster() {
        return new StateChangeHandler(timer, eventLog);
    }

    public ClusterFixture bringEntireClusterUp() {
        cluster.clusterInfo().getConfiguredNodes().forEach((idx, node) -> {
            reportStorageNodeState(idx, State.UP);
            reportDistributorNodeState(idx, State.UP);
        });
        return this;
    }

    ClusterFixture markEntireClusterDown() {
        cluster.clusterInfo().getConfiguredNodes().forEach((idx, node) -> {
            reportStorageNodeState(idx, State.DOWN);
            reportDistributorNodeState(idx, State.DOWN);
        });
        return this;
    }

    private void doReportNodeState(final Node node, final NodeState nodeState) {
        final ClusterState stateBefore = rawGeneratedClusterState();

        NodeStateOrHostInfoChangeHandler handler = mock(NodeStateOrHostInfoChangeHandler.class);
        NodeInfo nodeInfo = cluster.getNodeInfo(node);

        nodeStateChangeHandler.handleNewReportedNodeState(stateBefore, nodeInfo, nodeState, handler);
        nodeInfo.setReportedState(nodeState, timer.getCurrentTimeInMillis());
    }

    ClusterFixture reportStorageNodeState(final int index, State state, String description) {
        final Node node = new Node(NodeType.STORAGE, index);
        final NodeState nodeState = new NodeState(NodeType.STORAGE, state);
        nodeState.setDescription(description);
        doReportNodeState(node, nodeState);
        return this;
    }

    ClusterFixture reportStorageNodeState(final int index, State state) {
        return reportStorageNodeState(index, state, "mockdesc");
    }

    ClusterFixture reportStorageNodeState(final int index, NodeState nodeState) {
        doReportNodeState(new Node(NodeType.STORAGE, index), nodeState);
        return this;
    }

    ClusterFixture reportDistributorNodeState(final int index, State state) {
        final Node node = new Node(NodeType.DISTRIBUTOR, index);
        final NodeState nodeState = new NodeState(NodeType.DISTRIBUTOR, state);
        doReportNodeState(node, nodeState);
        return this;
    }

    ClusterFixture reportDistributorNodeState(final int index, NodeState nodeState) {
        doReportNodeState(new Node(NodeType.DISTRIBUTOR, index), nodeState);
        return this;
    }

    private void doProposeWantedState(final Node node, final NodeState nodeState, String description) {
        final ClusterState stateBefore = rawGeneratedClusterState();

        nodeState.setDescription(description);
        NodeInfo nodeInfo = cluster.getNodeInfo(node);
        nodeInfo.setWantedState(nodeState);

        nodeStateChangeHandler.proposeNewNodeState(stateBefore, nodeInfo, nodeState);
    }

    ClusterFixture proposeStorageNodeWantedState(final int index, State state, String description) {
        final Node node = new Node(NodeType.STORAGE, index);
        final NodeState nodeState = new NodeState(NodeType.STORAGE, state);
        doProposeWantedState(node, nodeState, description);
        return this;
    }

    ClusterFixture proposeStorageNodeWantedState(final int index, State state) {
        return proposeStorageNodeWantedState(index, state, "mockdesc");
    }

    ClusterFixture proposeDistributorWantedState(final int index, State state) {
        final ClusterState stateBefore = rawGeneratedClusterState();
        final Node node = new Node(NodeType.DISTRIBUTOR, index);
        final NodeState nodeState = new NodeState(NodeType.DISTRIBUTOR, state);
        nodeState.setDescription("mockdesc");
        NodeInfo nodeInfo = cluster.getNodeInfo(node);
        nodeInfo.setWantedState(nodeState);

        nodeStateChangeHandler.proposeNewNodeState(stateBefore, nodeInfo, nodeState);
        return this;
    }

    void disableAutoClusterTakedown() {
        setMinNodesUp(0, 0, 0.0, 0.0);
    }

    void setMinNodesUp(int minDistNodes, int minStorNodes, double minDistRatio, double minStorRatio) {
        params.minStorageNodesUp(minStorNodes)
              .minDistributorNodesUp(minDistNodes)
              .minRatioOfStorageNodesUp(minStorRatio)
              .minRatioOfDistributorNodesUp(minDistRatio);
    }

    void setMinNodeRatioPerGroup(double upRatio) {
        params.minNodeRatioPerGroup(upRatio);
    }

    public ClusterFixture assignDummyRpcAddresses() {
        cluster.getNodeInfo().forEach(ni -> {
            ni.setRpcAddress(String.format("tcp/%s.%d.local:0",
                    ni.isStorage() ? "storage" : "distributor",
                    ni.getNodeIndex()));
        });
        return this;
    }

    static Map<NodeType, Integer> buildTransitionTimeMap(int distributorTransitionTime, int storageTransitionTime) {
        Map<NodeType, Integer> maxTransitionTime = new TreeMap<>();
        maxTransitionTime.put(NodeType.DISTRIBUTOR, distributorTransitionTime);
        maxTransitionTime.put(NodeType.STORAGE, storageTransitionTime);
        return maxTransitionTime;
    }

    void disableTransientMaintenanceModeOnDown() {
        this.params.transitionTimes(0);
    }

    void enableTransientMaintenanceModeOnDown(final int transitionTimeMs) {
        this.params.transitionTimes(transitionTimeMs);
    }

    ClusterFixture markNodeAsConfigRetired(int nodeIndex) {
        Set<ConfiguredNode> configuredNodes = new HashSet<>(cluster.getConfiguredNodes().values());
        configuredNodes.remove(new ConfiguredNode(nodeIndex, false));
        configuredNodes.add(new ConfiguredNode(nodeIndex, true));
        cluster.setNodes(configuredNodes);
        return this;
    }

    AnnotatedClusterState annotatedGeneratedClusterState() {
        params.currentTimeInMilllis(timer.getCurrentTimeInMillis());
        return ClusterStateGenerator.generatedStateFrom(params);
    }

    private ClusterState rawGeneratedClusterState() {
        return annotatedGeneratedClusterState().getClusterState();
    }

    String generatedClusterState() {
        return annotatedGeneratedClusterState().getClusterState().toString();
    }

    String verboseGeneratedClusterState() {
        return annotatedGeneratedClusterState().getClusterState().toString(true);
    }

    public static ClusterFixture forFlatCluster(int nodeCount) {
        Collection<ConfiguredNode> nodes = DistributionBuilder.buildConfiguredNodes(nodeCount);

        Distribution distribution = DistributionBuilder.forFlatCluster(nodeCount);
        ContentCluster cluster = new ContentCluster("foo", nodes, distribution);

        return new ClusterFixture(cluster, distribution);
    }

    static ClusterFixture forHierarchicCluster(DistributionBuilder.GroupBuilder root) {
        List<ConfiguredNode> nodes = DistributionBuilder.buildConfiguredNodes(root.totalNodeCount());
        Distribution distribution = DistributionBuilder.forHierarchicCluster(root);
        ContentCluster cluster = new ContentCluster("foo", nodes, distribution);

        return new ClusterFixture(cluster, distribution);
    }

    ClusterStateGenerator.Params generatorParams() {
        return new ClusterStateGenerator.Params().cluster(cluster);
    }

    public ContentCluster cluster() {
        return this.cluster;
    }

    public static Node storageNode(int index) {
        return new Node(NodeType.STORAGE, index);
    }

    public static Node distributorNode(int index) {
        return new Node(NodeType.DISTRIBUTOR, index);
    }
}
