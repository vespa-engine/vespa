// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.prelude.Pong;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.prelude.fastsearch.test.MockMetric;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.PingFactory;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.vespa.config.search.DispatchConfig;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Callable;

import static com.yahoo.search.dispatch.MockSearchCluster.createDispatchConfig;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author ollivir
 */
public class DispatcherTest {
    private static final CompoundName internalDispatch = new CompoundName("dispatch.internal");

    private static Query query() {
        Query q = new Query();
        q.properties().set(internalDispatch, "true");
        return q;
    }

    @Test
    public void requireDispatcherToIgnoreMultilevelConfigurations() {
        SearchCluster searchCluster = new MockSearchCluster("1", 2, 2);
        DispatchConfig dispatchConfig = new DispatchConfig.Builder().useMultilevelDispatch(true).build();

        var invokerFactory = new MockInvokerFactory(searchCluster);

        Dispatcher disp = new Dispatcher(searchCluster, dispatchConfig, invokerFactory, invokerFactory, new MockMetric());
        assertThat(disp.getSearchInvoker(query(), null).isPresent(), is(false));
    }

    @Test
    public void requireThatDispatcherSupportsSearchPath() {
        SearchCluster cl = new MockSearchCluster("1", 2, 2);
        Query q = query();
        q.getModel().setSearchPath("1/0"); // second node in first group
        MockInvokerFactory invokerFactory = new MockInvokerFactory(cl, (nodes, a) -> {
            assertThat(nodes.size(), is(1));
            assertThat(nodes.get(0).key(), is(2));
            return true;
        });
        Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, invokerFactory, new MockMetric());
        Optional<SearchInvoker> invoker = disp.getSearchInvoker(q, null);
        assertThat(invoker.isPresent(), is(true));
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
        Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, invokerFactory, new MockMetric());
        Optional<SearchInvoker> invoker = disp.getSearchInvoker(query(), null);
        assertThat(invoker.isPresent(), is(true));
        invokerFactory.verifyAllEventsProcessed();
    }

    @Test
    public void requireThatInvokerConstructionIsRetriedAndLastAcceptsAnyCoverage() {
        SearchCluster cl = new MockSearchCluster("1", 2, 1);

        MockInvokerFactory invokerFactory = new MockInvokerFactory(cl, (n, acceptIncompleteCoverage) -> {
            assertThat(acceptIncompleteCoverage, is(false));
            return false;
        }, (n, acceptIncompleteCoverage) -> {
            assertThat(acceptIncompleteCoverage, is(true));
            return true;
        });
        Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, invokerFactory, new MockMetric());
        Optional<SearchInvoker> invoker = disp.getSearchInvoker(query(), null);
        assertThat(invoker.isPresent(), is(true));
        invokerFactory.verifyAllEventsProcessed();
    }

    @Test
    public void requireThatInvokerConstructionDoesNotRepeatGroups() {
        SearchCluster cl = new MockSearchCluster("1", 2, 1);

        MockInvokerFactory invokerFactory = new MockInvokerFactory(cl, (n, a) -> false, (n, a) -> false);
        Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, invokerFactory, new MockMetric());
        Optional<SearchInvoker> invoker = disp.getSearchInvoker(query(), null);
        assertThat(invoker.isPresent(), is(false));
        invokerFactory.verifyAllEventsProcessed();
    }

    interface FactoryStep {
        public boolean returnInvoker(List<Node> nodes, boolean acceptIncompleteCoverage);
    }

    private static class MockInvokerFactory extends InvokerFactory implements PingFactory {

        private final FactoryStep[] events;
        private int step = 0;

        public MockInvokerFactory(SearchCluster cl, FactoryStep... events) {
            super(cl);
            this.events = events;
        }

        @Override
        public Optional<SearchInvoker> createSearchInvoker(VespaBackEndSearcher searcher, Query query, OptionalInt groupId,
                List<Node> nodes, boolean acceptIncompleteCoverage) {
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
            assertThat(step, is(events.length));
        }

        @Override
        protected Optional<SearchInvoker> createNodeSearchInvoker(VespaBackEndSearcher searcher, Query query, Node node) {
            fail("Unexpected call to createNodeSearchInvoker");
            return null;
        }

        @Override
        public Optional<FillInvoker> createFillInvoker(VespaBackEndSearcher searcher, Result result) {
            fail("Unexpected call to createFillInvoker");
            return null;
        }

        @Override
        public Callable<Pong> createPinger(Node node, ClusterMonitor<Node> monitor) {
            fail("Unexpected call to createPinger");
            return null;
        }
    }
}
