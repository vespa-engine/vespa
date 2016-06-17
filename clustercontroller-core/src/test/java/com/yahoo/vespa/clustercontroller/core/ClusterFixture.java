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

    public ClusterFixture(ContentCluster cluster, Distribution distribution) {
        this.cluster = cluster;
        this.distribution = distribution;
        this.timer = new FakeTimer();
        this.eventLog = mock(EventLogInterface.class);
        this.generator = createGeneratorForFixtureCluster();
    }

    public SystemStateGenerator createGeneratorForFixtureCluster() {
        final int controllerIndex = 0;
        MetricUpdater metricUpdater = new MetricUpdater(new NoMetricReporter(), controllerIndex);
        SystemStateGenerator generator = new SystemStateGenerator(timer, eventLog, metricUpdater);
        generator.setNodes(cluster.clusterInfo());
        generator.setDistribution(distribution);
        return generator;
    }

    public void bringEntireClusterUp() {
        cluster.clusterInfo().getConfiguredNodes().forEach((idx, node) -> {
            reportStorageNodeState(idx, State.UP);
            reportDistributorNodeState(idx, State.UP);
        });
    }

    public void reportStorageNodeState(final int index, State state) {
        final Node node = new Node(NodeType.STORAGE, index);
        final NodeState nodeState = new NodeState(NodeType.STORAGE, state);
        nodeState.setDescription("mockdesc");
        NodeStateOrHostInfoChangeHandler handler = mock(NodeStateOrHostInfoChangeHandler.class);
        NodeInfo nodeInfo = cluster.getNodeInfo(node);

        generator.handleNewReportedNodeState(nodeInfo, nodeState, handler);
        nodeInfo.setReportedState(nodeState, timer.getCurrentTimeInMillis());
    }

    public void reportStorageNodeState(final int index, NodeState nodeState) {
        final Node node = new Node(NodeType.STORAGE, index);
        final NodeInfo nodeInfo = cluster.getNodeInfo(node);
        final long mockTime = 1234;
        NodeStateOrHostInfoChangeHandler changeListener = mock(NodeStateOrHostInfoChangeHandler.class);
        generator.handleNewReportedNodeState(nodeInfo, nodeState, changeListener);
        nodeInfo.setReportedState(nodeState, mockTime);
    }

    public void reportDistributorNodeState(final int index, State state) {
        final Node node = new Node(NodeType.DISTRIBUTOR, index);
        final NodeState nodeState = new NodeState(NodeType.DISTRIBUTOR, state);
        NodeStateOrHostInfoChangeHandler handler = mock(NodeStateOrHostInfoChangeHandler.class);
        NodeInfo nodeInfo = cluster.getNodeInfo(node);

        generator.handleNewReportedNodeState(nodeInfo, nodeState, handler);
        nodeInfo.setReportedState(nodeState, timer.getCurrentTimeInMillis());
    }

    public void proposeStorageNodeWantedState(final int index, State state) {
        final Node node = new Node(NodeType.STORAGE, index);
        final NodeState nodeState = new NodeState(NodeType.STORAGE, state);
        nodeState.setDescription("mockdesc");
        NodeInfo nodeInfo = cluster.getNodeInfo(node);
        nodeInfo.setWantedState(nodeState);

        generator.proposeNewNodeState(nodeInfo, nodeState);

    }

    public void disableAutoClusterTakedown() {
        generator.setMinNodesUp(0, 0, 0.0, 0.0);
    }

    public void disableTransientMaintenanceModeOnDown() {
        Map<NodeType, Integer> maxTransitionTime = new TreeMap<>();
        maxTransitionTime.put(NodeType.DISTRIBUTOR, 0);
        maxTransitionTime.put(NodeType.STORAGE, 0);
        generator.setMaxTransitionTime(maxTransitionTime);
    }

    public void enableTransientMaintenanceModeOnDown(final int transitionTime) {
        Map<NodeType, Integer> maxTransitionTime = new TreeMap<>();
        maxTransitionTime.put(NodeType.DISTRIBUTOR, transitionTime);
        maxTransitionTime.put(NodeType.STORAGE, transitionTime);
        generator.setMaxTransitionTime(maxTransitionTime);
    }

    public String generatedClusterState() {
        return generator.getClusterState().toString();
    }

    public String verboseGeneratedClusterState() { return generator.getClusterState().toString(true); }

    public static ClusterFixture forFlatCluster(int nodeCount) {
        Collection<ConfiguredNode> nodes = DistributionBuilder.buildConfiguredNodes(nodeCount);

        Distribution distribution = DistributionBuilder.forFlatCluster(nodeCount);
        ContentCluster cluster = new ContentCluster("foo", nodes, distribution, 0, 0.0);

        return new ClusterFixture(cluster, distribution);
    }

    public static ClusterFixture forHierarchicCluster(DistributionBuilder.GroupBuilder root) {
        List<ConfiguredNode> nodes = DistributionBuilder.buildConfiguredNodes(root.totalNodeCount());
        Distribution distribution = DistributionBuilder.forHierarchicCluster(root);
        ContentCluster cluster = new ContentCluster("foo", nodes, distribution, 0, 0.0);

        return new ClusterFixture(cluster, distribution);
    }
}
