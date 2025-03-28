// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.handler.ClustersStatus;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.net.HostName;
import com.yahoo.prelude.Pong;
import com.yahoo.search.cluster.BaseNodeMonitor;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.result.ErrorMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author baldersheim
 */
public class SearchClusterTest {

    static class State implements AutoCloseable {

        final String clusterId;
        final int nodesPerGroup;
        final VipStatus vipStatus;
        final SearchCluster searchCluster;
        final ClusterMonitor<Node> clusterMonitor;
        final List<AtomicInteger> numDocsPerNode;
        List<AtomicInteger> pingCounts;

        State(String clusterId, int nodesPergroup, String ... nodeNames) {
            this(clusterId, nodesPergroup, List.of(nodeNames));
        }

        State(String clusterId, int nodesPerGroup, List<String> nodeNames) {
            this.clusterId = clusterId;
            this.nodesPerGroup = nodesPerGroup;
            vipStatus = new VipStatus(new QrSearchersConfig.Builder().searchcluster(new QrSearchersConfig.Searchcluster.Builder().name(clusterId)).build(),
                                      new ClustersStatus());
            numDocsPerNode = new ArrayList<>(nodeNames.size());
            pingCounts = new ArrayList<>(nodeNames.size());
            List<Node> nodes = new ArrayList<>(nodeNames.size());

            for (String name : nodeNames) {
                int key = nodes.size() % nodesPerGroup;
                int group = nodes.size() / nodesPerGroup;
                nodes.add(new Node("test", key, name, group));
                numDocsPerNode.add(new AtomicInteger(1));
                pingCounts.add(new AtomicInteger(0));
            }
            searchCluster = new SearchCluster(clusterId,
                                              new AvailabilityPolicy(true, 100.0),
                                              nodes,
                                              vipStatus,
                                              new Factory(nodesPerGroup, numDocsPerNode, pingCounts));
            clusterMonitor = new ClusterMonitor<>(searchCluster, false);
            searchCluster.addMonitoring(clusterMonitor);
        }

        private int maxPingCount() {
            int max = pingCounts.get(0).get();
            for (AtomicInteger count : pingCounts) {
                if (count.get() > max) {
                    max = count.get();
                }
            }
            return max;
        }

        private int minPingCount() {
            int min = pingCounts.get(0).get();
            for (AtomicInteger count : pingCounts) {
                if (count.get() < min) {
                    min = count.get();
                }
            }
            return min;
        }

        void waitOneFullPingRound() {
            int minPingCount = minPingCount();
            int atLeast = maxPingCount() + 1;
            while (minPingCount < atLeast) {
                ExecutorService executor = Executors.newCachedThreadPool();
                clusterMonitor.ping(executor);
                executor.shutdown();
                try {
                    boolean completed = executor.awaitTermination(120, TimeUnit.SECONDS);
                    if ( ! completed )
                        throw new IllegalStateException("Ping thread timed out");
                    // Since a separate thread will be modifying values in pingCounts, we need to wait for the thread to
                    // finish before re-reading the minimum value
                    minPingCount = minPingCount();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void close() {
            clusterMonitor.shutdown();
        }

        static class Factory implements PingFactory {

            static class PingJob implements Pinger {

                private final AtomicInteger numDocs;
                private final AtomicInteger pingCount;
                private final PongHandler pongHandler;
                PingJob(AtomicInteger numDocs, AtomicInteger pingCount, PongHandler pongHandler) {
                    this.numDocs = numDocs;
                    this.pingCount = pingCount;
                    this.pongHandler = pongHandler;
                }
                @Override
                public void ping() {
                    int docs = numDocs.get();
                    pongHandler.handle ((docs < 0)
                            ? new Pong(ErrorMessage.createBackendCommunicationError("Negative numDocs = " + docs))
                            : new Pong(docs, docs));
                    pingCount.incrementAndGet();
                }
            }

            private final List<AtomicInteger> activeDocs;
            private final List<AtomicInteger> pingCounts;
            private final int numPerGroup;

            Factory(int numPerGroup, List<AtomicInteger> activeDocs, List<AtomicInteger> pingCounts) {
                this.numPerGroup = numPerGroup;
                this.activeDocs = activeDocs;
                this.pingCounts = pingCounts;
            }

            @Override
            public Pinger createPinger(Node node, ClusterMonitor<Node> monitor, PongHandler pongHandler) {
                int index = node.group() * numPerGroup + node.key();
                return new PingJob(activeDocs.get(index), pingCounts.get(index), pongHandler);
            }
        }

    }

    @Test
    void requireThatVipStatusWorksWhenReconfiguredFromZeroNodes() {
        try (State test = new State("test", 2, "a", "b")) {
            test.clusterMonitor.start();
            test.searchCluster.updateNodes(new AvailabilityPolicy(true, 100.0),
                                           List.of(),
                                           test.clusterMonitor);
            assertEquals(Set.of(), test.searchCluster.groupList().nodes());

            test.searchCluster.updateNodes(new AvailabilityPolicy(true, 100.0),
                                           List.of(new Node("test", 0, "a", 0),
                                                   new Node("test", 1, "b", 0)),
                                           test.clusterMonitor);
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    @Test
    void requireThatVipStatusIsDefaultDownButComesUpAfterPinging() {
        try (State test = new State("cluster.1", 2, "a", "b")) {
            assertTrue(test.searchCluster.localCorpusDispatchTarget().isEmpty());

            assertFalse(test.vipStatus.isInRotation());
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    @Test
    void requireThatZeroDocsAreFine() {
        try (State test = new State("cluster.1", 2, "a", "b")) {
            test.waitOneFullPingRound();

            assertTrue(test.vipStatus.isInRotation());
            assertTrue(test.searchCluster.localCorpusDispatchTarget().isEmpty());

            test.numDocsPerNode.get(0).set(-1);
            test.numDocsPerNode.get(1).set(-1);
            test.waitOneFullPingRound();
            assertFalse(test.vipStatus.isInRotation());
            test.numDocsPerNode.get(0).set(0);
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    @Test
    void requireThatVipStatusIsDefaultDownWithLocalDispatch() {
        try (State test = new State("cluster.1", 1, HostName.getLocalhost(), "b")) {
            assertFalse(test.vipStatus.isInRotation());
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    @Test
    void requireThatVipStatusStaysUpWithLocalDispatchAndClusterSize1() {
        try (State test = new State("cluster.1", 1, HostName.getLocalhost())) {
            assertFalse(test.vipStatus.isInRotation());
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
            test.numDocsPerNode.get(0).set(-1);
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    @Test
    void requireThatVipStatusIsDefaultDownWithLocalDispatchAndClusterSize2() {
        try (State test = new State("cluster.1", 1, HostName.getLocalhost(), "otherhost")) {
            assertFalse(test.vipStatus.isInRotation());
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
            test.numDocsPerNode.get(0).set(-1);
            test.waitOneFullPingRound();
            assertFalse(test.vipStatus.isInRotation());
        }
    }

    @Test
    void requireThatVipStatusDownWhenLocalIsDown() {
        try (State test = new State("cluster.1", 1, HostName.getLocalhost(), "b")) {

            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
            assertTrue(test.searchCluster.localCorpusDispatchTarget().isPresent());

            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
            test.numDocsPerNode.get(0).set(-1);
            test.waitOneFullPingRound();
            assertFalse(test.vipStatus.isInRotation());

            test.numDocsPerNode.get(0).set(1);
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());

            test.numDocsPerNode.get(1).set(-1);
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());

            test.numDocsPerNode.get(0).set(-1);
            test.numDocsPerNode.get(1).set(-1);
            test.waitOneFullPingRound();
            assertFalse(test.vipStatus.isInRotation());
            test.numDocsPerNode.get(1).set(1);
            test.waitOneFullPingRound();
            assertFalse(test.vipStatus.isInRotation());
            test.numDocsPerNode.get(0).set(1);
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    private void verifyThatVipStatusDownRequireAllNodesDown(int numGroups, int nodesPerGroup) {
        List<String> nodeNames = generateNodeNames(numGroups, nodesPerGroup);

        try (State test = new State("cluster.1", nodesPerGroup, nodeNames)) {
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
            assertTrue(test.searchCluster.localCorpusDispatchTarget().isEmpty());

            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());

            for (int i = 0; i < test.numDocsPerNode.size() - 1; i++) {
                test.numDocsPerNode.get(i).set(-1);
            }
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
            test.numDocsPerNode.get(test.numDocsPerNode.size() - 1).set(-1);
            test.waitOneFullPingRound();
            assertFalse(test.vipStatus.isInRotation());
        }
    }

    @Test
    void requireThatVipStatusDownRequireAllNodesDown() {
        verifyThatVipStatusDownRequireAllNodesDown(1, 2);
        verifyThatVipStatusDownRequireAllNodesDown(3, 3);
    }

    static private List<String> generateNodeNames(int numGroups, int nodesPerGroup) {
        List<String> nodeNames = new ArrayList<>(numGroups*nodesPerGroup);
        for (int g = 0; g < numGroups; g++) {
            for (int n = 0; n < nodesPerGroup; n++) {
                nodeNames.add("node." + g + '.' + n);
            }
        }
        return nodeNames;
    }

    private void verifyThatVipStatusUpRequireOnlyOneOnlineNode(int numGroups, int nodesPerGroup) {
        List<String> nodeNames = generateNodeNames(numGroups, nodesPerGroup);

        try (State test = new State("cluster.1", nodesPerGroup, nodeNames)) {
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
            assertTrue(test.searchCluster.localCorpusDispatchTarget().isEmpty());

            for (int i = 0; i < test.numDocsPerNode.size() - 1; i++) {
                test.numDocsPerNode.get(i).set(-1);
            }
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
            test.numDocsPerNode.get(test.numDocsPerNode.size() - 1).set(-1);
            test.waitOneFullPingRound();
            assertFalse(test.vipStatus.isInRotation());

            test.numDocsPerNode.get(0).set(0);
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    @Test
    void requireThatVipStatusUpRequireOnlyOneOnlineNode() {
        verifyThatVipStatusUpRequireOnlyOneOnlineNode(1, 2);
        verifyThatVipStatusUpRequireOnlyOneOnlineNode(3, 3);
    }

    @Test
    void requireThatPingSequenceIsUpHeld() {
        Node node = new Node("test", 1, "n", 1);
        assertEquals(1, node.createPingSequenceId());
        assertEquals(2, node.createPingSequenceId());
        assertEquals(0, node.getLastReceivedPongId());
        assertTrue(node.isLastReceivedPong(2));
        assertEquals(2, node.getLastReceivedPongId());
        assertFalse(node.isLastReceivedPong(1));
        assertFalse(node.isLastReceivedPong(2));
        assertTrue(node.isLastReceivedPong(3));
        assertEquals(3, node.getLastReceivedPongId());
    }

    @Test
    void requireThatEmptyGroupIsInBalance() {
        Group group = new Group(0, List.of());
        assertTrue(group.isBalanced());
        group.aggregateNodeValues();
        assertTrue(group.isBalanced());
    }

    @Test
    void requireThatSingleNodeGroupIsInBalance() {
        Group group = new Group(0, List.of(new Node("test", 1, "n", 1)));
        group.nodes().forEach(node -> node.setWorking(true));
        assertTrue(group.isBalanced());
        group.aggregateNodeValues();
        assertTrue(group.isBalanced());
        group.nodes().get(0).setActiveDocuments(1000);
        group.aggregateNodeValues();
        assertTrue(group.isBalanced());
    }

    @Test
    void requireThatMultiNodeGroupDetectsBalance() {
        Group group = new Group(0, List.of(new Node("test", 1, "n1", 1), new Node("test", 2, "n2", 1)));
        assertTrue(group.isBalanced());
        group.nodes().forEach(node -> node.setWorking(true));
        assertTrue(group.isBalanced());
        group.aggregateNodeValues();
        assertTrue(group.isBalanced());
        group.nodes().get(0).setActiveDocuments(1000);
        group.aggregateNodeValues();
        assertFalse(group.isBalanced());
        group.nodes().get(1).setActiveDocuments(100);
        group.aggregateNodeValues();
        assertFalse(group.isBalanced());
        group.nodes().get(1).setActiveDocuments(800);
        group.aggregateNodeValues();
        assertFalse(group.isBalanced());
        group.nodes().get(1).setActiveDocuments(818);
        group.aggregateNodeValues();
        assertFalse(group.isBalanced());
        group.nodes().get(1).setActiveDocuments(819);
        group.aggregateNodeValues();
        assertTrue(group.isBalanced());
    }

    @Test
    void requireThatPreciselyTheRetainedNodesAreKeptWhenNodesAreUpdated() {
        try (State state = new State("query", 2, IntStream.range(0, 6).mapToObj(i -> "node-" + i).toList())) {
            state.clusterMonitor.start();
            List<Node> referenceNodes = List.of(new Node("test", 0, "node-0", 0),
                                                new Node("test", 1, "node-1", 0),
                                                new Node("test", 0, "node-2", 1),
                                                new Node("test", 1, "node-3", 1),
                                                new Node("test", 0, "node-4", 2),
                                                new Node("test", 1, "node-5", 2));
            SearchGroups oldGroups = state.searchCluster.groupList();
            assertEquals(Set.copyOf(referenceNodes), oldGroups.nodes());
            List<BaseNodeMonitor<Node>> oldMonitors = state.clusterMonitor.nodeMonitors();

            List<Node> updatedNodes = List.of(new Node("test", 0, "node-1", 0),  // Swap node-0 and node-1
                                              new Node("test", 1, "node-0", 0),  // Swap node-1 and node-0
                                              new Node("test", 0, "node-4", 1),  // Swap node-2 and node-4
                                              new Node("test", 1, "node-3", 1),
                                              new Node("test", 0, "node-2", 2),  // Swap node-4 and node-2
                                              new Node("test", 1, "node-6", 2)); // Replace node-6
            state.searchCluster.updateNodes(new AvailabilityPolicy(true, 100.0),
                                            updatedNodes,
                                            state.clusterMonitor);
            SearchGroups newGroups = state.searchCluster.groupList();
            assertEquals(Set.copyOf(updatedNodes), newGroups.nodes());

            Map<Node, Node> oldNodesByIdentity = oldGroups.nodes().stream().collect(toMap(identity(), identity()));
            Map<Node, Node> newNodesByIdentity = newGroups.nodes().stream().collect(toMap(identity(), identity()));
            assertSame(updatedNodes.get(0), newNodesByIdentity.get(updatedNodes.get(0)));
            assertSame(updatedNodes.get(1), newNodesByIdentity.get(updatedNodes.get(1)));
            assertSame(updatedNodes.get(2), newNodesByIdentity.get(updatedNodes.get(2)));
            assertSame(oldNodesByIdentity.get(updatedNodes.get(3)), newNodesByIdentity.get(updatedNodes.get(3)));
            assertSame(updatedNodes.get(4), newNodesByIdentity.get(updatedNodes.get(4)));
            assertSame(updatedNodes.get(5), newNodesByIdentity.get(updatedNodes.get(5)));

            // Also verify search-path index within group follows node order, as given by config.
            int[] pathIndexWithinGroup = new int[3];
            for (Node node : updatedNodes)
                assertEquals(pathIndexWithinGroup[node.group()]++, newNodesByIdentity.get(node).pathIndex(),
                             "search path index within group should follow updated node order for: " + node);

            // Precisely the one retained node keeps its monitor through reconfiguration.
            Set<BaseNodeMonitor<Node>> retainedMonitors = new HashSet<>(state.clusterMonitor.nodeMonitors());
            assertEquals(6, retainedMonitors.size());
            retainedMonitors.retainAll(oldMonitors);
            assertEquals(1, retainedMonitors.size());
            assertSame(oldNodesByIdentity.get(updatedNodes.get(3)), retainedMonitors.iterator().next().getNode());
        }
    }

}
