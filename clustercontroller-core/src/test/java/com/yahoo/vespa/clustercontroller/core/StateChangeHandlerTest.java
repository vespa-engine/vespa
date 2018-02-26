// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;
import com.yahoo.vespa.clustercontroller.core.mocks.TestEventLog;
import com.yahoo.vespa.clustercontroller.core.testutils.LogFormatter;
import org.junit.Before;
import org.junit.Test;


import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StateChangeHandlerTest {

    private static final Logger log = Logger.getLogger(StateChangeHandlerTest.class.getName());
    private class Config {
        int nodeCount = 3;
        int stableStateTime = 1000 * 60000;
        int maxSlobrokDisconnectPeriod = 60000;
        int maxPrematureCrashes = 3;
    }

    private class TestNodeStateOrHostInfoChangeHandler implements NodeStateOrHostInfoChangeHandler {

        LinkedList<String> events = new LinkedList<>();

        @Override
        public void handleNewNodeState(NodeInfo node, NodeState newState) {
            events.add(node + " - " + newState);
        }

        @Override
        public void handleNewWantedNodeState(NodeInfo node, NodeState newState) {
            events.add(node + " - " + newState);
        }

        @Override
        public void handleUpdatedHostInfo(NodeInfo node, HostInfo newHostInfo) {
            events.add(node + " - " + newHostInfo);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("NodeChanges(");
            for (String change : events) sb.append('\n').append(change);
            sb.append(")");
            return sb.toString();
        }
    }

    private FakeTimer clock = new FakeTimer();
    private TestEventLog eventLog = new TestEventLog();
    private Set<ConfiguredNode> configuredNodes = new TreeSet<>();
    private Config config;
    private ContentCluster cluster;
    private StateChangeHandler nodeStateChangeHandler;
    private TestNodeStateOrHostInfoChangeHandler nodeStateUpdateListener;
    private final ClusterStateGenerator.Params params = new ClusterStateGenerator.Params();

    @Before
    public void setUp() {
        LogFormatter.initializeLogging();
    }

    private void initialize(Config config) {
        Distribution distribution = new Distribution(Distribution.getDefaultDistributionConfig(2, 100));
        this.config = config;
        for (int i=0; i<config.nodeCount; ++i) configuredNodes.add(new ConfiguredNode(i, false));
        cluster = new ContentCluster("testcluster", configuredNodes, distribution, 0, 0.0);
        nodeStateChangeHandler = new StateChangeHandler(clock, eventLog, null);
        params.minStorageNodesUp(1).minDistributorNodesUp(1)
                .minRatioOfStorageNodesUp(0.0).minRatioOfDistributorNodesUp(0.0)
                .maxPrematureCrashes(config.maxPrematureCrashes)
                .transitionTimes(5000)
                .cluster(cluster);
        nodeStateUpdateListener = new TestNodeStateOrHostInfoChangeHandler();
    }

    private ClusterState currentClusterState() {
        params.currentTimeInMilllis(clock.getCurrentTimeInMillis());
        return ClusterStateGenerator.generatedStateFrom(params).getClusterState();
    }

    private void startWithStableStateClusterWithNodesUp() {
        for (NodeType type : NodeType.getTypes()) {
            for (ConfiguredNode i : configuredNodes) {
                NodeInfo nodeInfo = cluster.clusterInfo().setRpcAddress(new Node(type, i.index()), null);
                nodeInfo.markRpcAddressLive();
                nodeStateChangeHandler.handleNewReportedNodeState(
                        currentClusterState(), nodeInfo, new NodeState(type, State.UP), null);
                nodeInfo.setReportedState(new NodeState(type, State.UP), clock.getCurrentTimeInMillis());
            }
        }
        for (NodeType type : NodeType.getTypes()) {
            for (ConfiguredNode i : configuredNodes) {
                Node n = new Node(type, i.index());
                assertEquals(State.UP, currentClusterState().getNodeState(n).getState());
            }
        }
        clock.advanceTime(config.stableStateTime);
    }

    private void markNodeOutOfSlobrok(Node node) {
        final ClusterState stateBefore = currentClusterState();
        log.info("Marking " + node + " out of slobrok");
        cluster.getNodeInfo(node).markRpcAddressOutdated(clock);
        nodeStateChangeHandler.handleMissingNode(stateBefore, cluster.getNodeInfo(node), nodeStateUpdateListener);
        assertTrue(eventLog.toString(), eventLog.toString().contains("Node is no longer in slobrok"));
        eventLog.clear();
    }

    private void markNodeBackIntoSlobrok(Node node, State state) {
        final ClusterState stateBefore = currentClusterState();
        log.info("Marking " + node + " back in slobrok");
        cluster.getNodeInfo(node).markRpcAddressLive();
        nodeStateChangeHandler.handleReturnedRpcAddress(cluster.getNodeInfo(node));
        nodeStateChangeHandler.handleNewReportedNodeState(
                stateBefore, cluster.getNodeInfo(node),
                new NodeState(node.getType(), state), nodeStateUpdateListener);
        cluster.getNodeInfo(node).setReportedState(new NodeState(node.getType(), state), clock.getCurrentTimeInMillis());
    }

    private void verifyClusterStateChanged(Node node, State state) {
        log.info("Verifying cluster state has been updated for " + node + " to " + state);
        assertTrue(nodeStateChangeHandler.stateMayHaveChanged());
        assertEquals(state, currentClusterState().getNodeState(node).getState());
    }

    private void verifyNodeStateAfterTimerWatch(Node node, State state) {
        log.info("Verifying state of node after timer watch.");
        nodeStateChangeHandler.watchTimers(cluster, currentClusterState(), nodeStateUpdateListener);
        assertEquals(0, nodeStateUpdateListener.events.size());
        verifyClusterStateChanged(node, state);
    }

    private void verifyPrematureCrashCountCleared(Node node) {
        assertTrue(nodeStateChangeHandler.watchTimers(cluster, currentClusterState(), nodeStateUpdateListener));
        assertEquals(0, cluster.getNodeInfo(node).getPrematureCrashCount());
    }

    @Test
    public void testUnstableNodeInSlobrok() {
        initialize(new Config());
        startWithStableStateClusterWithNodesUp();
        Node node = new Node(NodeType.STORAGE, 0);
        for (int j=0; j<3; ++j) {
            log.info("Iteration " + j);
            assertEquals(0, cluster.getNodeInfo(node).getPrematureCrashCount());
            assertEquals(State.UP, cluster.getNodeInfo(node).getWantedState().getState());
            assertEquals(State.UP, currentClusterState().getNodeState(node).getState());
            for (int k=0; k<config.maxPrematureCrashes; ++k) {
                log.info("Premature iteration " + k);
                markNodeOutOfSlobrok(node);

                log.info("Passing max disconnect time period. Watching timers");
                clock.advanceTime(config.maxSlobrokDisconnectPeriod);
                verifyNodeStateAfterTimerWatch(node, State.MAINTENANCE);

                cluster.getNodeInfo(node).setReportedState(new NodeState(node.getType(), State.DOWN), clock.getCurrentTimeInMillis());

                assertEquals(k, cluster.getNodeInfo(node).getPrematureCrashCount());
                markNodeBackIntoSlobrok(node, State.UP);
                verifyClusterStateChanged(node, State.UP);
            }
            log.info("Passing steady state to get premature crash count flag cleared");
            clock.advanceTime(config.stableStateTime);
            verifyPrematureCrashCountCleared(node);
        }
    }

}
