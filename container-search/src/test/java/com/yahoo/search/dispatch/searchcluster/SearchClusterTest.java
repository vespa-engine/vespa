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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
            searchCluster = new SearchCluster(clusterId, MockSearchCluster.createDispatchConfig(nodes), nodes.size() / nodesPerGroup, vipStatus);
        }

        void startMonitoring() {
            searchCluster.startClusterMonitoring(new Factory(nodesPerGroup, numDocsPerNode, pingCounts));
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
                searchCluster.clusterMonitor().ping(executor);
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
            searchCluster.shutDown();
        }

        static class Factory implements PingFactory {

            static class Pinger implements Callable<Pong> {

                private final AtomicInteger numDocs;
                private final AtomicInteger pingCount;
                Pinger(AtomicInteger numDocs, AtomicInteger pingCount) {
                    this.numDocs = numDocs;
                    this.pingCount = pingCount;
                }
                @Override
                public Pong call() {
                    int docs = numDocs.get();
                    pingCount.incrementAndGet();
                    return (docs < 0)
                            ? new Pong(ErrorMessage.createBackendCommunicationError("Negative numDocs = " + docs))
                            : new Pong(docs);
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
            public Callable<Pong> createPinger(Node node, ClusterMonitor<Node> monitor) {
                int index = node.group() * numPerGroup + node.key();
                return new Pinger(activeDocs.get(index), pingCounts.get(index));
            }
        }

    }

    @Test
    public void requireThatVipStatusIsDefaultDownButComesUpAfterPinging() {
        try (State test = new State("cluster.1", 2, "a", "b")) {
            assertTrue(test.searchCluster.localCorpusDispatchTarget().isEmpty());

            assertFalse(test.vipStatus.isInRotation());
            test.startMonitoring();
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    @Test
    public void requireThatZeroDocsAreFine() {
        try (State test = new State("cluster.1", 2, "a", "b")) {
            test.startMonitoring();
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
            test.startMonitoring();
            test.waitOneFullPingRound();
            assertTrue(test.vipStatus.isInRotation());
        }
    }

    @Test
    public void requireThatVipStatusIsDefaultDownWithOnlySingleLocalDispatch() {
        try (State test = new State("cluster.1", 1, HostName.getLocalhost())) {
            assertTrue(test.searchCluster.localCorpusDispatchTarget().isPresent());

            assertFalse(test.vipStatus.isInRotation());
            test.startMonitoring();
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

            test.startMonitoring();
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
            test.startMonitoring();
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
            test.startMonitoring();
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

}
