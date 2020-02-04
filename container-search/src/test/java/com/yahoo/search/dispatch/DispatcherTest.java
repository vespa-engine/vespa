// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.prelude.fastsearch.test.MockMetric;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.PingFactory;
import com.yahoo.search.dispatch.searchcluster.Pinger;
import com.yahoo.search.dispatch.searchcluster.PongHandler;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.yahoo.search.dispatch.MockSearchCluster.createDispatchConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author ollivir
 */
public class DispatcherTest {

    @Test
    public void requireThatDispatcherSupportsSearchPath() {
        SearchCluster cl = new MockSearchCluster("1", 2, 2);
        Query q = new Query();
        q.getModel().setSearchPath("1/0"); // second node in first group
        MockInvokerFactory invokerFactory = new MockInvokerFactory(cl, (nodes, a) -> {
            assertEquals(1, nodes.size());
            assertEquals(2, nodes.get(0).key());
            return true;
        });
        Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, new MockMetric());
        SearchInvoker invoker = disp.getSearchInvoker(q, null);
        invokerFactory.verifyAllEventsProcessed();
    }

    @Test
    public void requireThatDispatcherSupportsSingleNodeDirectDispatch() {
        SearchCluster cl = new MockSearchCluster("1", 0, 0) {
            @Override
            public Optional<Node> localCorpusDispatchTarget() {
                return Optional.of(new Node(1, "test", 1));
            }
        };
        MockInvokerFactory invokerFactory = new MockInvokerFactory(cl, (n, a) -> true);
        Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, new MockMetric());
        SearchInvoker invoker = disp.getSearchInvoker(new Query(), null);
        invokerFactory.verifyAllEventsProcessed();
    }

    @Test
    public void requireThatInvokerConstructionIsRetriedAndLastAcceptsAnyCoverage() {
        SearchCluster cl = new MockSearchCluster("1", 2, 1);

        MockInvokerFactory invokerFactory = new MockInvokerFactory(cl, (n, acceptIncompleteCoverage) -> {
            assertFalse(acceptIncompleteCoverage);
            return false;
        }, (n, acceptIncompleteCoverage) -> {
            assertTrue(acceptIncompleteCoverage);
            return true;
        });
        Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, new MockMetric());
        SearchInvoker invoker = disp.getSearchInvoker(new Query(), null);
        invokerFactory.verifyAllEventsProcessed();
    }

    @Test
    public void requireThatInvokerConstructionDoesNotRepeatGroups() {
        try {
            SearchCluster cl = new MockSearchCluster("1", 2, 1);

            MockInvokerFactory invokerFactory = new MockInvokerFactory(cl, (n, a) -> false, (n, a) -> false);
            Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, new MockMetric());
            disp.getSearchInvoker(new Query(), null);
            fail("Expected exception");
        }
        catch (IllegalStateException e) {
            assertEquals("No suitable groups to dispatch query. Rejected: [0, 1]", e.getMessage());
        }
    }

    interface FactoryStep {
        boolean returnInvoker(List<Node> nodes, boolean acceptIncompleteCoverage);
    }

    private static class MockInvokerFactory extends InvokerFactory implements PingFactory {

        private final FactoryStep[] events;
        private int step = 0;

        public MockInvokerFactory(SearchCluster cl, FactoryStep... events) {
            super(cl);
            this.events = events;
        }

        @Override
        public Optional<SearchInvoker> createSearchInvoker(VespaBackEndSearcher searcher,
                                                           Query query,
                                                           OptionalInt groupId,
                                                           List<Node> nodes,
                                                           boolean acceptIncompleteCoverage,
                                                           int maxHitsPerNode) {
            if (step >= events.length) {
                throw new RuntimeException("Was not expecting more calls to getSearchInvoker");
            }
            boolean nonEmpty = events[step].returnInvoker(nodes, acceptIncompleteCoverage);
            step++;
            if (nonEmpty) {
                return Optional.of(new MockInvoker(1));
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
            return null;
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
