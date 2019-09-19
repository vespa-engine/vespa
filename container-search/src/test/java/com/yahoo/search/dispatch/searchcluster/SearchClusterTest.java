package com.yahoo.search.dispatch.searchcluster;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.handler.ClustersStatus;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.prelude.Pong;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.MockSearchCluster;
import com.yahoo.search.result.ErrorMessage;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchClusterTest {

    static class State {
        final String clusterId;
        final VipStatus vipStatus;
        final SearchCluster sc;
        final List<AtomicInteger> numDocsPerNode;
        List<AtomicInteger> pingCounts;
        State(String clusterId) {
            this.clusterId = clusterId;
            vipStatus = new VipStatus(new QrSearchersConfig.Builder().searchcluster(new QrSearchersConfig.Searchcluster.Builder().name(clusterId)).build(), new ClustersStatus());
            assertFalse(vipStatus.isInRotation());
            vipStatus.addToRotation(clusterId);
            assertTrue(vipStatus.isInRotation());
            sc = new SearchCluster(clusterId, MockSearchCluster.createDispatchConfig(new Node(0, "a",13333,0), new Node(1, "b",13333,0)),1, vipStatus);
            numDocsPerNode = Arrays.asList(new AtomicInteger(1), new AtomicInteger(1));
            pingCounts = Arrays.asList(new AtomicInteger(0), new AtomicInteger(0));
        }
        void startMonitoring() {
            sc.startClusterMonitoring(new Factory(numDocsPerNode, pingCounts));
        }
        private static int getMaxValue(List<AtomicInteger> list) {
            int max = list.get(0).get();
            for (AtomicInteger v : list) {
                if (v.get() > max) {
                    max = v.get();
                }
            }
            return max;
        }
        private static int getMinValue(List<AtomicInteger> list) {
            int min = list.get(0).get();
            for (AtomicInteger v : list) {
                if (v.get() < min) {
                    min = v.get();
                }
            }
            return min;
        }
        private static void waitAtLeast(int atLeast, List<AtomicInteger> list) {
            while (getMinValue(list) < atLeast) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {}
            }
        }
        void waitOneFullPingRound() {
            waitAtLeast(getMaxValue(pingCounts) + 1, pingCounts);
        }
    }

    @Test
    public void requireThatVipStatusIsDefaultUp() {
        State test = new State("1");
        assertTrue(test.vipStatus.isInRotation());
    }

    @Test
    public void requireThatVipStatusDownRequireAllNodesDown() {
        State test = new State("1");
        assertTrue(test.vipStatus.isInRotation());

        test.startMonitoring();
        test.waitOneFullPingRound();
        assertTrue(test.vipStatus.isInRotation());
        test.numDocsPerNode.get(0).set(-1);
        test.waitOneFullPingRound();
        assertTrue(test.vipStatus.isInRotation());
        test.numDocsPerNode.get(1).set(-1);
        test.waitOneFullPingRound();
        assertFalse(test.vipStatus.isInRotation());
    }

    @Test
    public void requireThatVipStatusUpRequireOnlyOneOnlineNode() {
        State test = new State("1");
        assertTrue(test.vipStatus.isInRotation());

        test.startMonitoring();
        test.numDocsPerNode.get(0).set(-1);
        test.numDocsPerNode.get(1).set(-1);
        test.waitOneFullPingRound();
        assertFalse(test.vipStatus.isInRotation());

        test.numDocsPerNode.get(0).set(0);
        test.waitOneFullPingRound();
        assertTrue(test.vipStatus.isInRotation());
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
            public Pong call() throws Exception {
                int docs = numDocs.get();
                pingCount.incrementAndGet();
                return (docs < 0)
                        ? new Pong(ErrorMessage.createBackendCommunicationError("Negative numDocs = " + docs))
                        : new Pong(docs);
            }
        }

        private final List<AtomicInteger> activeDocs;
        private final List<AtomicInteger> pingCounts;
        Factory(List<AtomicInteger> activeDocs, List<AtomicInteger> pingCounts) {
            this.activeDocs = activeDocs;
            this.pingCounts = pingCounts;
        }

        @Override
        public Callable<Pong> createPinger(Node node, ClusterMonitor<Node> monitor) {
            return new Pinger(activeDocs.get(node.pathIndex()), pingCounts.get(node.pathIndex()));
        }
    }
}
