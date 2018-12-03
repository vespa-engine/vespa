// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.yahoo.search.dispatch.MockSearchCluster.createDispatchConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author ollivir
 */
public class InterleavedSearchInvokerTest {
    private ManualClock clock = new ManualClock(Instant.now());
    private Query query = new TestQuery();
    private LinkedList<Event> expectedEvents = new LinkedList<>();
    private List<SearchInvoker> invokers = new ArrayList<>();

    @Test
    public void requireThatAdaptiveTimeoutsAreNotUsedWithFullCoverageRequirement() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", createDispatchConfig(100.0), 1, 3);
        SearchInvoker invoker = createInterleavedInvoker(cluster, 3);

        expectedEvents.add(new Event(5000, 100, 0));
        expectedEvents.add(new Event(4900, 100, 1));
        expectedEvents.add(new Event(4800, 100, 2));

        invoker.search(query, null, null, null);

        assertTrue("All test scenario events processed", expectedEvents.isEmpty());
    }

    @Test
    public void requireThatTimeoutsAreNotMarkedAsAdaptive() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", createDispatchConfig(100.0), 1, 3);
        SearchInvoker invoker = createInterleavedInvoker(cluster, 3);

        expectedEvents.add(new Event(5000, 300, 0));
        expectedEvents.add(new Event(4700, 300, 1));
        expectedEvents.add(null);

        Result result = invoker.search(query, null, null, null);

        assertTrue("All test scenario events processed", expectedEvents.isEmpty());
        assertNotNull("Result is marked as an error", result.hits().getErrorHit());
        assertTrue("Degradation reason is a normal timeout", result.getCoverage(false).isDegradedByTimeout());
    }

    @Test
    public void requireThatAdaptiveTimeoutDecreasesTimeoutWhenCoverageIsReached() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", createDispatchConfig(50.0), 1, 4);
        SearchInvoker invoker = createInterleavedInvoker(cluster, 4);

        expectedEvents.add(new Event(5000, 100, 0));
        expectedEvents.add(new Event(4900, 100, 1));
        expectedEvents.add(new Event(2400, 100, 2));
        expectedEvents.add(new Event(0, 0, null));

        Result result = invoker.search(query, null, null, null);

        assertTrue("All test scenario events processed", expectedEvents.isEmpty());
        assertNotNull("Result is marked as an error", result.hits().getErrorHit());
        assertTrue("Degradataion reason is an adaptive timeout", result.getCoverage(false).isDegradedByAdapativeTimeout());
    }

    private InterleavedSearchInvoker createInterleavedInvoker(SearchCluster searchCluster, int numInvokers) {
        for (int i = 0; i < numInvokers; i++) {
            invokers.add(new TestInvoker());
        }

        return new InterleavedSearchInvoker(invokers, null, searchCluster) {
            @Override
            protected long currentTime() {
                return clock.millis();
            }

            @Override
            protected LinkedBlockingQueue<SearchInvoker> newQueue() {
                return new LinkedBlockingQueue<SearchInvoker>() {
                    @Override
                    public SearchInvoker poll(long timeout, TimeUnit timeUnit) throws InterruptedException {
                        assertFalse(expectedEvents.isEmpty());
                        Event ev = expectedEvents.removeFirst();
                        if (ev == null) {
                            return null;
                        } else {
                            return ev.process(query, timeout);
                        }
                    }
                };
            }
        };
    }

    private class Event {
        Long expectedTimeout;
        long delay;
        Integer invokerIndex;

        public Event(Integer expectedTimeout, int delay, Integer invokerIndex) {
            this.expectedTimeout = (long) expectedTimeout;
            this.delay = delay;
            this.invokerIndex = invokerIndex;
        }

        public SearchInvoker process(Query query, long currentTimeout) {
            if (expectedTimeout != null) {
                assertEquals("Expecting timeout to be " + expectedTimeout, (long) expectedTimeout, currentTimeout);
            }
            clock.advance(Duration.ofMillis(delay));
            if (query.getTimeLeft() < 0) {
                fail("Test sequence ran out of time window");
            }
            if (invokerIndex == null) {
                return null;
            } else {
                return invokers.get(invokerIndex);
            }
        }
    }

    private class TestInvoker extends SearchInvoker {
        protected TestInvoker() {
            super(Optional.of(new Node(42, "?", 0, 0)));
        }

        @Override
        protected void sendSearchRequest(Query query, QueryPacket queryPacket) throws IOException {
        }

        @Override
        protected Result getSearchResult(CacheKey cacheKey, Execution execution) throws IOException {
            return new Result(query);
        }

        @Override
        protected void release() {
        }
    }

    public class TestQuery extends Query {
        private long start = clock.millis();

        public TestQuery() {
            super();
            setTimeout(5000);
        }

        @Override
        public long getStartTime() {
            return start;
        }

        @Override
        public long getDurationTime() {
            return clock.millis() - start;
        }
    }
}
