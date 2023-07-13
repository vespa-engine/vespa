// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.compress.CompressionType;
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.Dispatcher.InvokerFactoryFactory;
import com.yahoo.search.dispatch.rpc.Client.NodeConnection;
import com.yahoo.search.dispatch.rpc.Client.ResponseReceiver;
import com.yahoo.search.dispatch.rpc.RpcConnectionPool;
import com.yahoo.search.dispatch.searchcluster.MockSearchCluster;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.PingFactory;
import com.yahoo.search.dispatch.searchcluster.Pinger;
import com.yahoo.search.dispatch.searchcluster.PongHandler;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.dispatch.searchcluster.SearchGroups;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.yahoo.search.dispatch.searchcluster.MockSearchCluster.createDispatchConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author ollivir
 */
public class DispatcherTest {
    private final DispatchConfig dispatchConfig = createDispatchConfig();

    @Test
    void requireThatDispatcherSupportsSearchPath() {
        SearchCluster cl = new MockSearchCluster("1", 2, 2);
        Query q = new Query();
        q.getModel().setSearchPath("1/0"); // second node in first group
        MockInvokerFactory invokerFactory = new MockInvokerFactory(cl.groupList(), dispatchConfig, (nodes, a) -> {
            assertEquals(1, nodes.size());
            assertEquals(1, nodes.get(0).key());
            return true;
        });
        Dispatcher disp = new Dispatcher(new ClusterMonitor<>(cl, false), cl, dispatchConfig, invokerFactory);
        SearchInvoker invoker = disp.getSearchInvoker(q, null);
        assertNotNull(invoker);
        invokerFactory.verifyAllEventsProcessed();
        disp.deconstruct();
    }

    @Test
    void requireThatDispatcherSupportsSingleNodeDirectDispatch() {
        SearchCluster cl = new MockSearchCluster("1", 0, 0) {
            @Override
            public Optional<Node> localCorpusDispatchTarget() {
                return Optional.of(new Node(1, "test", 1));
            }
        };
        MockInvokerFactory invokerFactory = new MockInvokerFactory(cl.groupList(), dispatchConfig, (n, a) -> true);
        Dispatcher disp = new Dispatcher(new ClusterMonitor<>(cl, false), cl, dispatchConfig, invokerFactory);
        SearchInvoker invoker = disp.getSearchInvoker(new Query(), null);
        assertNotNull(invoker);
        invokerFactory.verifyAllEventsProcessed();
        disp.deconstruct();
    }

    @Test
    void requireThatInvokerConstructionIsRetriedAndLastAcceptsAnyCoverage() {
        SearchCluster cl = new MockSearchCluster("1", 2, 1);

        MockInvokerFactory invokerFactory = new MockInvokerFactory(cl.groupList(), dispatchConfig, (n, acceptIncompleteCoverage) -> {
            assertFalse(acceptIncompleteCoverage);
            return false;
        }, (n, acceptIncompleteCoverage) -> {
            assertTrue(acceptIncompleteCoverage);
            return true;
        });
        Dispatcher disp = new Dispatcher(new ClusterMonitor<>(cl, false), cl, dispatchConfig, invokerFactory);
        SearchInvoker invoker = disp.getSearchInvoker(new Query(), null);
        assertNotNull(invoker);
        invokerFactory.verifyAllEventsProcessed();
        disp.deconstruct();
    }

    @Test
    void requireThatInvokerConstructionDoesNotRepeatGroups() {
        try {
            SearchCluster cl = new MockSearchCluster("1", 2, 1);

            MockInvokerFactory invokerFactory = new MockInvokerFactory(cl.groupList(), dispatchConfig, (n, a) -> false, (n, a) -> false);
            Dispatcher disp = new Dispatcher(new ClusterMonitor<>(cl, false), cl, dispatchConfig, invokerFactory);
            disp.getSearchInvoker(new Query(), null);
            disp.deconstruct();
            fail("Expected exception");
        }
        catch (IllegalStateException e) {
            assertEquals("No suitable groups to dispatch query. Rejected: [0, 1]", e.getMessage());
        }
    }

    @Test
    void testGroup0IsSelected() {
        SearchCluster cluster = new MockSearchCluster("1", 3, 1);
        Dispatcher dispatcher = new Dispatcher(new ClusterMonitor<>(cluster, false), cluster, dispatchConfig,
                new MockInvokerFactory(cluster.groupList(), dispatchConfig, (n, a) -> true));
        cluster.pingIterationCompleted();
        assertEquals(0,
                dispatcher.getSearchInvoker(new Query(), null).distributionKey().get().longValue());
        dispatcher.deconstruct();
    }

    @Test
    void testGroup0IsSkippedWhenItIsBlockingFeed() {
        SearchCluster cluster = new MockSearchCluster("1", 3, 1);
        Dispatcher dispatcher = new Dispatcher(new ClusterMonitor<>(cluster, false), cluster, dispatchConfig,
                new MockInvokerFactory(cluster.groupList(), dispatchConfig, (n, a) -> true));
        cluster.group(0).nodes().get(0).setBlockingWrites(true);
        cluster.pingIterationCompleted();
        assertEquals(1,
                (dispatcher.getSearchInvoker(new Query(), null).distributionKey().get()).longValue(),
                "Blocking group is avoided");
        dispatcher.deconstruct();
    }

    @Test
    void testGroup0IsSelectedWhenMoreAreBlockingFeed() {
        SearchCluster cluster = new MockSearchCluster("1", 3, 1);
        Dispatcher dispatcher = new Dispatcher(new ClusterMonitor<>(cluster, false), cluster, dispatchConfig,
                new MockInvokerFactory(cluster.groupList(), dispatchConfig, (n, a) -> true));
        cluster.group(0).nodes().get(0).setBlockingWrites(true);
        cluster.group(1).nodes().get(0).setBlockingWrites(true);
        cluster.pingIterationCompleted();
        assertEquals(0,
                dispatcher.getSearchInvoker(new Query(), null).distributionKey().get().longValue(),
                "Blocking group is used when multiple groups are blocking");
        dispatcher.deconstruct();
    }

    @Test
    void testGroup0IsSelectedWhenItIsBlockingFeedWhenNoOthers() {
        SearchCluster cluster = new MockSearchCluster("1", 1, 1);
        Dispatcher dispatcher = new Dispatcher(new ClusterMonitor<>(cluster, false), cluster, dispatchConfig,
                new MockInvokerFactory(cluster.groupList(), dispatchConfig, (n, a) -> true));
        cluster.group(0).nodes().get(0).setBlockingWrites(true);
        cluster.pingIterationCompleted();
        assertEquals(0,
                (dispatcher.getSearchInvoker(new Query(), null).distributionKey().get()).longValue(),
                "Blocking group is used when there is no alternative");
        dispatcher.deconstruct();
    }

    @Test
    void testRpcResourceShutdownOnReconfiguration() throws InterruptedException, ExecutionException, IOException {
        // Ping factory lets us tick each ping, so we may delay shutdown, due to monitor thread RPC usage.
        Map<Integer, Phaser> pingPhasers = new ConcurrentHashMap<>();
        pingPhasers.put(0, new Phaser(2));
        pingPhasers.put(1, new Phaser(2));
        pingPhasers.put(2, new Phaser(2));

        PingFactory pingFactory = (node, monitor, pongHandler) -> () -> {
            pingPhasers.get(node.key()).arriveAndAwaitAdvance();
            pongHandler.handle(new Pong(2, 2));
            pingPhasers.get(node.key()).arriveAndAwaitAdvance();
        };

        // Search cluster uses the ping factory, and zero nodes initially, later configured with two nodes.
        SearchCluster cluster = new MockSearchCluster("cid", 0, 1, pingFactory);

        // Dummy RPC layer where we manually tick responses for each node.
        // When a response is let go, we verify the RPC resource is not yet closed.
        // This is signalled by terminating its phaser, which is done by the dispatcher in delayed cleanup.
        // We verify in the end that all connections have been shut down, prior to shutting down the RPC pool proper.
        Map<Integer, Boolean > rpcResources = new HashMap<>();
        AtomicLong cleanupThreadId = new AtomicLong();
        AtomicInteger nodeIdOfSearcher0 = new AtomicInteger(-1);
        RpcConnectionPool rpcPool = new RpcConnectionPool() {
            // Returns a connection that lets us advance the searcher when we want to, as well as tracking which threads do what.
            @Override public NodeConnection getConnection(int nodeId) {
                nodeIdOfSearcher0.set(nodeId);
                return new NodeConnection() {
                    @Override public void request(String rpcMethod, CompressionType compression, int uncompressedLength, byte[] compressedPayload, ResponseReceiver responseReceiver, double timeoutSeconds) {
                        assertTrue(rpcResources.get(nodeId));
                    }
                    @Override public void close() {
                        assertFalse(rpcResources.remove(nodeId));
                    }
                };
            }
            // Verifies cleanup is done by the expected thread, by ID, and cleans up the "RPC connection" (phaser).
            @Override public Collection<? extends AutoCloseable> updateNodes(DispatchNodesConfig config) {
                for (DispatchNodesConfig.Node node : config.node())
                    rpcResources.putIfAbsent(node.key(), true);
                return rpcResources.keySet().stream()
                                   .filter(key -> config.node().stream().noneMatch(node -> node.key() == key))
                                   .map(key -> (AutoCloseable) () -> {
                                       assertTrue(rpcResources.put(key, false));
                                       cleanupThreadId.set(Thread.currentThread().getId());
                                       getConnection(key).close();
                                   })
                                   .toList();
            };
            // In the end, we have reconfigured down to 0 nodes, and no resources should be left running after cleanup.
            @Override public void close() {
                assertEquals(Map.of(), rpcResources);
            }
        };

        // This factory just forwards search to the dummy RPC layer above, nothing more.
        InvokerFactoryFactory invokerFactories = (rpcConnectionPool, searchGroups, dispatchConfig) -> new InvokerFactory(searchGroups, dispatchConfig) {
            @Override protected Optional<SearchInvoker> createNodeSearchInvoker(VespaBackEndSearcher searcher, Query query, int maxHits, Node node) {
                return Optional.of(new SearchInvoker(Optional.of(node)) {
                    @Override protected Object sendSearchRequest(Query query, Object context) {
                        rpcPool.getConnection(node.key()).request(null, null, 0, null, null, 0);
                        return null;
                    };
                    @Override protected InvokerResult getSearchResult(Execution execution) {
                        return new InvokerResult(new Result(new Query()));
                    }
                    @Override protected void release() { }
                });
            };
            @Override public FillInvoker createFillInvoker(VespaBackEndSearcher searcher, Result result) {
                return new FillInvoker() {
                    @Override protected void getFillResults(Result result, String summaryClass) { fail(); }
                    @Override protected void sendFillRequest(Result result, String summaryClass) { fail(); }
                    @Override protected void release() { fail(); }
                };
            }
        };

        Dispatcher dispatcher = new Dispatcher(dispatchConfig, rpcPool, cluster, invokerFactories);
        ExecutorService executor = Executors.newFixedThreadPool(1);

        // Set two groups with a single node each. The first cluster-monitor has nothing to do, and is shut down immediately.
        // There are also no invokers, so the whole reconfiguration completes once the new cluster monitor has seen all nodes.
        Future<?> reconfiguration = executor.submit(() -> {
            dispatcher.updateWithNewConfig(new DispatchNodesConfig.Builder()
                                                   .node(new DispatchNodesConfig.Node.Builder().key(0).group(0).port(123).host("host0"))
                                                   .node(new DispatchNodesConfig.Node.Builder().key(1).group(1).port(123).host("host1"))
                                                   .build());
        });

        // Let pings return, to allow the search cluster to reconfigure.
        pingPhasers.get(0).arriveAndAwaitAdvance();
        pingPhasers.get(0).arriveAndAwaitAdvance();
        pingPhasers.get(1).arriveAndAwaitAdvance();
        pingPhasers.get(1).arriveAndAwaitAdvance();
        // We need to wait for the cluster to have at least one group, lest dispatch will fail below.
        reconfiguration.get();
        assertNotEquals(cleanupThreadId.get(), Thread.currentThread().getId());
        assertEquals(1, cluster.group(0).workingNodes());
        assertEquals(1, cluster.group(1).workingNodes());

        Node node0 = cluster.group(0).nodes().get(0); // Node0 will be replaced.
        Node node1 = cluster.group(1).nodes().get(0); // Node1 will be retained.

        // Start some searches, one against each group, since we have a round-robin policy.
        SearchInvoker search0 = dispatcher.getSearchInvoker(new Query(), null);
        search0.search(new Query(), null);
        // Unknown whether the first or second search hits node0, so we must track that.
        int offset = nodeIdOfSearcher0.get();
        SearchInvoker search1 = dispatcher.getSearchInvoker(new Query(), null);
        search1.search(new Query(), null);

        // Wait for the current cluster monitor to be mid-ping-round.
        pingPhasers.get(0).arriveAndAwaitAdvance();

        // Then reconfigure the dispatcher with new nodes, replacing node0 with node2.
        reconfiguration = executor.submit(() -> {
            dispatcher.updateWithNewConfig(new DispatchNodesConfig.Builder()
                                                   .node(new DispatchNodesConfig.Node.Builder().key(2).group(0).port(123).host("host2"))
                                                   .node(new DispatchNodesConfig.Node.Builder().key(1).group(1).port(123).host("host1"))
                                                   .build());
        });
        // Reconfiguration starts, but groups are only updated once the search cluster has knowledge about all of them.
        pingPhasers.get(1).arriveAndAwaitAdvance();
        pingPhasers.get(1).arriveAndAwaitAdvance();
        pingPhasers.get(2).arriveAndAwaitAdvance();
        // Cluster has not yet updated its group reference.
        assertEquals(1, cluster.group(0).workingNodes()); // Node0 is still working.
        assertSame(node0, cluster.group(0).nodes().get(0));
        pingPhasers.get(2).arriveAndAwaitAdvance();

        // Old cluster monitor is waiting for that ping to complete before it can shut down, and let reconfiguration complete.
        pingPhasers.get(0).arriveAndAwaitAdvance();
        reconfiguration.get();
        Node node2 = cluster.group(0).nodes().get(0);
        assertNotSame(node0, node2);
        assertSame(node1, cluster.group(1).nodes().get(0));

        // Next search should hit group0 again, this time on node2.
        SearchInvoker search2 = dispatcher.getSearchInvoker(new Query(), null);
        search2.search(new Query(), null);

        // Searches against nodes 1 and 2 complete.
        (offset == 0 ? search0 : search1).close();
        search2.close();

        // We're still waiting for search against node0 to complete, before we can shut down its RPC connection.
        assertEquals(Set.of(0, 1, 2), rpcResources.keySet());
        (offset == 0 ? search1 : search0).close();
        // Thread for search 0 should have closed the RPC pool now.
        assertEquals(Set.of(1, 2), rpcResources.keySet());
        assertEquals(cleanupThreadId.get(), Thread.currentThread().getId());

        // Finally, reconfigure down to 0 nodes.
        reconfiguration = executor.submit(() -> {
            cleanupThreadId.set(Thread.currentThread().getId());
            dispatcher.updateWithNewConfig(new DispatchNodesConfig.Builder().build());
        });
        pingPhasers.get(1).forceTermination();
        pingPhasers.get(2).forceTermination();
        reconfiguration.get();
        assertNotEquals(cleanupThreadId.get(), Thread.currentThread().getId());
        dispatcher.deconstruct();
    }

    interface FactoryStep {
        boolean returnInvoker(List<Node> nodes, boolean acceptIncompleteCoverage);
    }

    private static class MockInvokerFactory extends InvokerFactory implements PingFactory {

        private final FactoryStep[] events;
        private int step = 0;

        public MockInvokerFactory(SearchGroups cl, DispatchConfig disptachConfig, FactoryStep... events) {
            super(cl, disptachConfig);
            this.events = events;
        }

        @Override
        public Optional<SearchInvoker> createSearchInvoker(VespaBackEndSearcher searcher,
                                                           Query query,
                                                           List<Node> nodes,
                                                           boolean acceptIncompleteCoverage,
                                                           int maxHitsPerNode) {
            if (step >= events.length) {
                throw new RuntimeException("Was not expecting more calls to getSearchInvoker");
            }
            boolean nonEmpty = events[step].returnInvoker(nodes, acceptIncompleteCoverage);
            step++;
            if (nonEmpty) {
                return Optional.of(new MockInvoker(nodes.get(0).key()));
            } else {
                return Optional.empty();
            }
        }

        void verifyAllEventsProcessed() {
            assertEquals(events.length, step);
        }

        @Override
        protected Optional<SearchInvoker> createNodeSearchInvoker(VespaBackEndSearcher searcher,
                                                                  Query query,
                                                                  int maxHitsPerNode,
                                                                  Node node) {
            fail("Unexpected call to createNodeSearchInvoker");
            return Optional.empty();
        }

        @Override
        public FillInvoker createFillInvoker(VespaBackEndSearcher searcher, Result result) {
            fail("Unexpected call to createFillInvoker");
            return null;
        }

        @Override
        public Pinger createPinger(Node node, ClusterMonitor<Node> monitor, PongHandler pongHandler) {
            fail("Unexpected call to createPinger");
            return null;
        }
    }

}
