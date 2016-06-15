// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.*;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;
import com.yahoo.vespa.clustercontroller.core.listeners.SystemStateListener;
import com.yahoo.vespa.clustercontroller.core.mocks.TestEventLog;
import com.yahoo.vespa.clustercontroller.core.testutils.LogFormatter;
import junit.framework.TestCase;

import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

public class SystemStateGeneratorTest extends TestCase {
    private static final Logger log = Logger.getLogger(SystemStateGeneratorTest.class.getName());
    class Config {
        int nodeCount = 3;
        int stableStateTime = 1000 * 60000;
        int maxSlobrokDisconnectPeriod = 60000;
        int maxPrematureCrashes = 3;
    }
    class TestSystemStateListener implements SystemStateListener {
        LinkedList<ClusterState> states = new LinkedList<>();

        @Override
        public void handleNewSystemState(ClusterState state) {
            states.add(state);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("States(");
            for (ClusterState state : states) sb.append('\n').append(state.toString());
            sb.append(")");
            return sb.toString();
        }

    }

    class TestNodeStateOrHostInfoChangeHandler implements NodeStateOrHostInfoChangeHandler {

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
    private SystemStateGenerator generator;
    private TestSystemStateListener systemStateListener;
    private TestNodeStateOrHostInfoChangeHandler nodeStateUpdateListener;

    public void setUp() {
        LogFormatter.initializeLogging();
    }

    private void initialize(Config config) {
        Distribution distribution = new Distribution(Distribution.getDefaultDistributionConfig(2, 100));
        this.config = config;
        for (int i=0; i<config.nodeCount; ++i) configuredNodes.add(new ConfiguredNode(i, false));
        cluster = new ContentCluster("testcluster", configuredNodes, distribution, 0, 0.0);
        generator = new SystemStateGenerator(clock, eventLog, null);
        generator.setNodes(cluster.clusterInfo());
        generator.setStableStateTimePeriod(config.stableStateTime);
        generator.setMaxPrematureCrashes(config.maxPrematureCrashes);
        generator.setMaxSlobrokDisconnectGracePeriod(config.maxSlobrokDisconnectPeriod);
        generator.setMinNodesUp(1, 1, 0, 0);
        systemStateListener = new TestSystemStateListener();
        nodeStateUpdateListener = new TestNodeStateOrHostInfoChangeHandler();
    }

    private void assertNewClusterStateReceived() {
        assertTrue(generator.notifyIfNewSystemState(systemStateListener));
        assertTrue(systemStateListener.toString(), systemStateListener.states.size() == 1);
        systemStateListener.states.clear();
    }

    private void startWithStableStateClusterWithNodesUp() {
        for (NodeType type : NodeType.getTypes()) {
            for (ConfiguredNode i : configuredNodes) {
                NodeInfo nodeInfo = cluster.clusterInfo().setRpcAddress(new Node(type, i.index()), null);
                nodeInfo.markRpcAddressLive();
                generator.handleNewReportedNodeState(nodeInfo, new NodeState(type, State.UP), null);
                nodeInfo.setReportedState(new NodeState(type, State.UP), clock.getCurrentTimeInMillis());
            }
        }
        assertNewClusterStateReceived();
        for (NodeType type : NodeType.getTypes()) {
            for (ConfiguredNode i : configuredNodes) {
                Node n = new Node(type, i.index());
                assertEquals(State.UP, generator.getClusterState().getNodeState(n).getState());
            }
        }
        clock.advanceTime(config.stableStateTime);
    }

    private void markNodeOutOfSlobrok(Node node) {
        log.info("Marking " + node + " out of slobrok");
        cluster.getNodeInfo(node).markRpcAddressOutdated(clock);
        generator.handleMissingNode(cluster.getNodeInfo(node), nodeStateUpdateListener);
        assertTrue(nodeStateUpdateListener.toString(), nodeStateUpdateListener.events.isEmpty());
        nodeStateUpdateListener.events.clear();
        assertTrue(eventLog.toString(), eventLog.toString().contains("Node is no longer in slobrok"));
        eventLog.clear();
    }

    private void markNodeBackIntoSlobrok(Node node, State state) {
        log.info("Marking " + node + " back in slobrok");
        cluster.getNodeInfo(node).markRpcAddressLive();
        generator.handleReturnedRpcAddress(cluster.getNodeInfo(node));
        assertEquals(0, nodeStateUpdateListener.events.size());
        assertEquals(0, systemStateListener.states.size());
        generator.handleNewReportedNodeState(cluster.getNodeInfo(node), new NodeState(node.getType(), state), nodeStateUpdateListener);
        cluster.getNodeInfo(node).setReportedState(new NodeState(node.getType(), state), clock.getCurrentTimeInMillis());
        assertEquals(0, nodeStateUpdateListener.events.size());
        assertEquals(0, systemStateListener.states.size());
    }

    private void verifyClusterStateChanged(Node node, State state) {
        log.info("Verifying cluster state has been updated for " + node + " to " + state);
        assertTrue(generator.notifyIfNewSystemState(systemStateListener));
        assertEquals(1, systemStateListener.states.size());
        assertEquals(state, systemStateListener.states.get(0).getNodeState(node).getState());
        systemStateListener.states.clear();
        assertEquals(state, generator.getClusterState().getNodeState(node).getState());
    }

    private void verifyNodeStateAfterTimerWatch(Node node, State state) {
        log.info("Verifying state of node after timer watch.");
        generator.watchTimers(cluster, nodeStateUpdateListener);
        assertEquals(0, nodeStateUpdateListener.events.size());
        verifyClusterStateChanged(node, state);
    }

    private void verifyPrematureCrashCountCleared(Node node) {
        assertTrue(generator.watchTimers(cluster, nodeStateUpdateListener));
        assertEquals(0, nodeStateUpdateListener.events.size());
        assertEquals(0, cluster.getNodeInfo(node).getPrematureCrashCount());
    }

    public void testUnstableNodeInSlobrok() throws Exception {
        initialize(new Config());
        startWithStableStateClusterWithNodesUp();
        Node node = new Node(NodeType.STORAGE, 0);
        for (int j=0; j<3; ++j) {
            log.info("Iteration " + j);
            assertEquals(0, cluster.getNodeInfo(node).getPrematureCrashCount());
            assertEquals(State.UP, cluster.getNodeInfo(node).getWantedState().getState());
            assertEquals(State.UP, generator.getClusterState().getNodeState(node).getState());
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
