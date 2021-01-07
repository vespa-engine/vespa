// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.handler.ClustersStatus;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.net.HostName;
import com.yahoo.prelude.Pong;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.MockSearchCluster;
import com.yahoo.search.result.ErrorMessage;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author baldersheim
 */
public class SearchClusterTest {

    static class State implements AutoCloseable{

        final String clusterId;
        final int nodesPerGroup;
        final VipStatus vipStatus;
        final SearchCluster searchCluster;
        final ClusterMonitor clusterMonitor;
        final List<AtomicInteger> numDocsPerNode;
        List<AtomicInteger> pingCounts;

        State(String clusterId, int nodesPergroup, String ... nodeNames) {
            this(clusterId, nodesPergroup, Arrays.asList(nodeNames));
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
                nodes.add(new Node(key, name, group));
                numDocsPerNode.add(new AtomicInteger(1));
                pingCounts.add(new AtomicInteger(0));
            }
            searchCluster = new SearchCluster(clusterId, MockSearchCluster.createDispatchConfig(nodes), nodes.size() / nodesPerGroup,
                                              vipStatus, new Factory(nodesPerGroup, numDocsPerNode, pingCounts));
            clusterMonitor = new ClusterMonitor(searchCluster, false);
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
                            : new Pong(docs));
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
    public void requireThatVipStatusIsDefaultDownButComesUpAfterPinging() {
        try (State test = new State("cluster.1", 2, "a", "b")) {
            assertTrue(test.searchCluster.localCorpusDispatchTarget().isEmpty());

            assertFalse(test.vipStatus.isInRotation());
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    @Test
    public void requireThatZeroDocsAreFine() {
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
    public void requireThatVipStatusIsDefaultDownWithLocalDispatch() {
        try (State test = new State("cluster.1", 1, HostName.getLocalhost(), "b")) {
            assertTrue(test.searchCluster.localCorpusDispatchTarget().isPresent());

            assertFalse(test.vipStatus.isInRotation());
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    @Test
    public void requireThatVipStatusStaysUpWithLocalDispatchAndClusterSize1() {
        try (State test = new State("cluster.1", 1, HostName.getLocalhost())) {
            assertTrue(test.searchCluster.localCorpusDispatchTarget().isPresent());

            assertFalse(test.vipStatus.isInRotation());
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
            test.numDocsPerNode.get(0).set(-1);
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    @Test
    public void requireThatVipStatusIsDefaultDownWithLocalDispatchAndClusterSize2() {
        try (State test = new State("cluster.1", 1, HostName.getLocalhost(), "otherhost")) {
            assertTrue(test.searchCluster.localCorpusDispatchTarget().isPresent());

            assertFalse(test.vipStatus.isInRotation());
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
            test.numDocsPerNode.get(0).set(-1);
            test.waitOneFullPingRound();
            assertFalse(test.vipStatus.isInRotation());
        }
    }

    @Test
    public void requireThatVipStatusDownWhenLocalIsDown() {
        try (State test = new State("cluster.1",1,HostName.getLocalhost(), "b")) {

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
    public void requireThatVipStatusDownRequireAllNodesDown() {
        verifyThatVipStatusDownRequireAllNodesDown(1,2);
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
    public void requireThatVipStatusUpRequireOnlyOneOnlineNode() {
        verifyThatVipStatusUpRequireOnlyOneOnlineNode(1, 2);
        verifyThatVipStatusUpRequireOnlyOneOnlineNode(3, 3);
    }

    @Test
    public void requireThatPingSequenceIsUpHeld() {
        Node node = new Node(1, "n", 1);
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
    public void requireThatEmptyGroupIsInBalance() {
        Group group = new Group(0, new ArrayList<>());
        assertTrue(group.isContentWellBalanced());
        group.aggregateNodeValues();
        assertTrue(group.isContentWellBalanced());
    }

    @Test
    public void requireThatSingleNodeGroupIsInBalance() {
        Group group = new Group(0, Arrays.asList(new Node(1, "n", 1)));
        group.nodes().forEach(node -> node.setWorking(true));
        assertTrue(group.isContentWellBalanced());
        group.aggregateNodeValues();
        assertTrue(group.isContentWellBalanced());
        group.nodes().get(0).setActiveDocuments(1000);
        group.aggregateNodeValues();
        assertTrue(group.isContentWellBalanced());
    }

    @Test
    public void requireThatMultiNodeGroupDetectsBalance() {
        Group group = new Group(0, Arrays.asList(new Node(1, "n1", 1), new Node(2, "n2", 1)));
        assertTrue(group.isContentWellBalanced());
        group.nodes().forEach(node -> node.setWorking(true));
        assertTrue(group.isContentWellBalanced());
        group.aggregateNodeValues();
        assertTrue(group.isContentWellBalanced());
        group.nodes().get(0).setActiveDocuments(1000);
        group.aggregateNodeValues();
        assertFalse(group.isContentWellBalanced());
        group.nodes().get(1).setActiveDocuments(100);
        group.aggregateNodeValues();
        assertFalse(group.isContentWellBalanced());
        group.nodes().get(1).setActiveDocuments(800);
        group.aggregateNodeValues();
        assertFalse(group.isContentWellBalanced());
        group.nodes().get(1).setActiveDocuments(818);
        group.aggregateNodeValues();
        assertFalse(group.isContentWellBalanced());
        group.nodes().get(1).setActiveDocuments(819);
        group.aggregateNodeValues();
        assertTrue(group.isContentWellBalanced());
    }
}
