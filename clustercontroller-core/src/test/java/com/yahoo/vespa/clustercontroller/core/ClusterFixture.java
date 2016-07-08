// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;
import com.yahoo.vespa.clustercontroller.core.mocks.TestEventLog;
import com.yahoo.vespa.clustercontroller.utils.util.NoMetricReporter;
import com.yahoo.vespa.config.content.StorDistributionConfig;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Mockito.mock;

class ClusterFixture {
    public final ContentCluster cluster;
    public final Distribution distribution;
    public final FakeTimer timer;
    public final EventLogInterface eventLog;
    public final SystemStateGenerator generator;

    ClusterFixture(ContentCluster cluster, Distribution distribution) {
        this.cluster = cluster;
        this.distribution = distribution;
        this.timer = new FakeTimer();
        this.eventLog = mock(EventLogInterface.class);
        this.generator = createGeneratorForFixtureCluster();
    }

    SystemStateGenerator createGeneratorForFixtureCluster() {
        final int controllerIndex = 0;
        MetricUpdater metricUpdater = new MetricUpdater(new NoMetricReporter(), controllerIndex);
        SystemStateGenerator generator = new SystemStateGenerator(timer, eventLog, metricUpdater);
        generator.setNodes(cluster.clusterInfo());
        generator.setDistribution(distribution);
        return generator;
    }

    ClusterFixture bringEntireClusterUp() {
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
        NodeStateOrHostInfoChangeHandler handler = mock(NodeStateOrHostInfoChangeHandler.class);
        NodeInfo nodeInfo = cluster.getNodeInfo(node);

        generator.handleNewReportedNodeState(nodeInfo, nodeState, handler);
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
        nodeState.setDescription(description);
        NodeInfo nodeInfo = cluster.getNodeInfo(node);
        nodeInfo.setWantedState(nodeState);

        generator.proposeNewNodeState(nodeInfo, nodeState);
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

    // TODO de-dupe
    ClusterFixture proposeDistributorWantedState(final int index, State state) {
        final Node node = new Node(NodeType.DISTRIBUTOR, index);
        final NodeState nodeState = new NodeState(NodeType.DISTRIBUTOR, state);
        nodeState.setDescription("mockdesc");
        NodeInfo nodeInfo = cluster.getNodeInfo(node);
        nodeInfo.setWantedState(nodeState);

        generator.proposeNewNodeState(nodeInfo, nodeState);
        return this;
    }

    void disableAutoClusterTakedown() {
        generator.setMinNodesUp(0, 0, 0.0, 0.0);
    }

    static Map<NodeType, Integer> buildTransitionTimeMap(int distributorTransitionTime, int storageTransitionTime) {
        Map<NodeType, Integer> maxTransitionTime = new TreeMap<>();
        maxTransitionTime.put(NodeType.DISTRIBUTOR, distributorTransitionTime);
        maxTransitionTime.put(NodeType.STORAGE, storageTransitionTime);
        return maxTransitionTime;
    }

    void disableTransientMaintenanceModeOnDown() {
        generator.setMaxTransitionTime(buildTransitionTimeMap(0, 0));
    }

    void enableTransientMaintenanceModeOnDown(final int transitionTime) {
        generator.setMaxTransitionTime(buildTransitionTimeMap(transitionTime, transitionTime));
    }

    String generatedClusterState() {
        return generator.getClusterState().toString();
    }

    String verboseGeneratedClusterState() { return generator.getClusterState().toString(true); }

    static ClusterFixture forFlatCluster(int nodeCount) {
        Collection<ConfiguredNode> nodes = DistributionBuilder.buildConfiguredNodes(nodeCount);

        Distribution distribution = DistributionBuilder.forFlatCluster(nodeCount);
        ContentCluster cluster = new ContentCluster("foo", nodes, distribution, 0, 0.0);

        return new ClusterFixture(cluster, distribution);
    }

    static ClusterFixture forHierarchicCluster(DistributionBuilder.GroupBuilder root) {
        List<ConfiguredNode> nodes = DistributionBuilder.buildConfiguredNodes(root.totalNodeCount());
        Distribution distribution = DistributionBuilder.forHierarchicCluster(root);
        ContentCluster cluster = new ContentCluster("foo", nodes, distribution, 0, 0.0);

        return new ClusterFixture(cluster, distribution);
    }

    ClusterStateGenerator.Params generatorParams() {
        ClusterStateGenerator.Params params = new ClusterStateGenerator.Params();
        params.cluster = cluster;
        return params;
    }
}
