// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.prelude.fastsearch.FS4InvokerFactory;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.vespa.config.search.DispatchConfig;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.yahoo.search.dispatch.MockSearchCluster.createDispatchConfig;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

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
        SearchCluster cl = new MockSearchCluster("1", 2, 2);
        DispatchConfig.Builder builder = new DispatchConfig.Builder();
        builder.useMultilevelDispatch(true);
        DispatchConfig dc = new DispatchConfig(builder);

        Dispatcher disp = new Dispatcher(cl, dc, new MockFS4InvokerFactory(cl), new MockRpcInvokerFactory());
        assertThat(disp.getSearchInvoker(query(), null).isPresent(), is(false));
    }

    @Test
    public void requireThatDispatcherSupportsSearchPath() {
        SearchCluster cl = new MockSearchCluster("1", 2, 2);
        Query q = query();
        q.getModel().setSearchPath("1/0"); // second node in first group
        MockFS4InvokerFactory invokerFactory = new MockFS4InvokerFactory(cl, (nodes, a) -> {
            assertThat(nodes.size(), is(1));
            assertThat(nodes.get(0).key(), is(2));
            return true;
        });
        Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, new MockRpcInvokerFactory());
        Optional<SearchInvoker> invoker = disp.getSearchInvoker(q, null);
        assertThat(invoker.isPresent(), is(true));
        invokerFactory.verifyAllEventsProcessed();
    }

    @Test
    public void requireThatDispatcherSupportsSingleNodeDirectDispatch() {
        SearchCluster cl = new MockSearchCluster("1", 0, 0) {
            @Override
            public Optional<Node> directDispatchTarget() {
                return Optional.of(new Node(1, "test", 123, 1));
            }
        };
        MockFS4InvokerFactory invokerFactory = new MockFS4InvokerFactory(cl, (n, a) -> true);
        Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, new MockRpcInvokerFactory());
        Optional<SearchInvoker> invoker = disp.getSearchInvoker(query(), null);
        assertThat(invoker.isPresent(), is(true));
        invokerFactory.verifyAllEventsProcessed();
    }

    @Test
    public void requireThatInvokerConstructionIsRetriedAndLastAcceptsAnyCoverage() {
        SearchCluster cl = new MockSearchCluster("1", 2, 1);

        MockFS4InvokerFactory invokerFactory = new MockFS4InvokerFactory(cl, (n, acceptIncompleteCoverage) -> {
            assertThat(acceptIncompleteCoverage, is(false));
            return false;
        }, (n, acceptIncompleteCoverage) -> {
            assertThat(acceptIncompleteCoverage, is(true));
            return true;
        });
        Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, new MockRpcInvokerFactory());
        Optional<SearchInvoker> invoker = disp.getSearchInvoker(query(), null);
        assertThat(invoker.isPresent(), is(true));
        invokerFactory.verifyAllEventsProcessed();
    }

    @Test
    public void requireThatInvokerConstructionDoesNotRepeatGroups() {
        SearchCluster cl = new MockSearchCluster("1", 2, 1);

        MockFS4InvokerFactory invokerFactory = new MockFS4InvokerFactory(cl, (n, a) -> false, (n, a) -> false);
        Dispatcher disp = new Dispatcher(cl, createDispatchConfig(), invokerFactory, null);
        Optional<SearchInvoker> invoker = disp.getSearchInvoker(query(), null);
        assertThat(invoker.isPresent(), is(false));
        invokerFactory.verifyAllEventsProcessed();
    }

    interface FactoryStep {
        public boolean returnInvoker(List<Node> nodes, boolean acceptIncompleteCoverage);
    }

    private static class MockFS4InvokerFactory extends FS4InvokerFactory {
        private final FactoryStep[] events;
        private int step = 0;

        public MockFS4InvokerFactory(SearchCluster cl, FactoryStep... events) {
            super(null, cl);
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
    }

    public class MockRpcInvokerFactory extends RpcInvokerFactory {
        public MockRpcInvokerFactory() {
            super(null, null);
        }

        @Override
        public void release() {
        }
    }
}
