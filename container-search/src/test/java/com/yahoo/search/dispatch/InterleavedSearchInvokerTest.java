// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
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
import java.util.stream.StreamSupport;

import static com.yahoo.container.handler.Coverage.DEGRADED_BY_MATCH_PHASE;
import static com.yahoo.container.handler.Coverage.DEGRADED_BY_TIMEOUT;
import static com.yahoo.search.dispatch.MockSearchCluster.createDispatchConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

        invoker.search(query, null, null);

        assertTrue("All test scenario events processed", expectedEvents.isEmpty());
    }

    @Test
    public void requireThatTimeoutsAreNotMarkedAsAdaptive() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", createDispatchConfig(100.0), 1, 3);
        SearchInvoker invoker = createInterleavedInvoker(cluster, 3);

        expectedEvents.add(new Event(5000, 300, 0));
        expectedEvents.add(new Event(4700, 300, 1));
        expectedEvents.add(null);

        Result result = invoker.search(query, null, null);

        assertTrue("All test scenario events processed", expectedEvents.isEmpty());
        assertNull("Result is not marked as an error", result.hits().getErrorHit());
        var message = findTrace(result, "Backend communication timeout");
        assertThat("Timeout should be reported in a trace message", message.isPresent());
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

        Result result = invoker.search(query, null, null);

        assertTrue("All test scenario events processed", expectedEvents.isEmpty());
        assertNull("Result is not marked as an error", result.hits().getErrorHit());
        var message = findTrace(result, "Backend communication timeout");
        assertThat("Timeout should be reported in a trace message", message.isPresent());
        assertTrue("Degradataion reason is an adaptive timeout", result.getCoverage(false).isDegradedByAdapativeTimeout());
    }

    @Test
    public void requireCorrectCoverageCalculationWhenAllNodesOk() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", 1, 2);
        invokers.add(new MockInvoker(0, createCoverage(50155, 50155, 50155, 1, 1, 0)));
        invokers.add(new MockInvoker(1, createCoverage(49845, 49845, 49845, 1, 1, 0)));
        SearchInvoker invoker = createInterleavedInvoker(cluster, 0);

        expectedEvents.add(new Event(null, 100, 0));
        expectedEvents.add(new Event(null, 200, 1));

        Result result = invoker.search(query, null, null);

        Coverage cov = result.getCoverage(true);
        assertThat(cov.getDocs(), is(100000L));
        assertThat(cov.getNodes(), is(2));
        assertThat(cov.getFull(), is(true));
        assertThat(cov.getResultPercentage(), is(100));
        assertThat(cov.getResultSets(), is(1));
        assertThat(cov.getFullResultSets(), is(1));
    }

    @Test
    public void requireCorrectCoverageCalculationWhenResultsAreLimitedByMatchPhase() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", 1, 2);
        invokers.add(new MockInvoker(0, createCoverage(10101, 50155, 50155, 1, 1, DEGRADED_BY_MATCH_PHASE)));
        invokers.add(new MockInvoker(1, createCoverage(13319, 49845, 49845, 1, 1, DEGRADED_BY_MATCH_PHASE)));
        SearchInvoker invoker = createInterleavedInvoker(cluster, 0);

        expectedEvents.add(new Event(null, 100, 0));
        expectedEvents.add(new Event(null, 200, 1));

        Result result = invoker.search(query, null, null);

        Coverage cov = result.getCoverage(true);
        assertThat(cov.getDocs(), is(23420L));
        assertThat(cov.getNodes(), is(2));
        assertThat(cov.getFull(), is(false));
        assertThat(cov.getResultPercentage(), is(23));
        assertThat(cov.getResultSets(), is(1));
        assertThat(cov.getFullResultSets(), is(0));
        assertThat(cov.isDegradedByMatchPhase(), is(true));
    }

    @Test
    public void requireCorrectCoverageCalculationWhenResultsAreLimitedBySoftTimeout() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", 1, 2);
        invokers.add(new MockInvoker(0, createCoverage(5000, 50155, 50155, 1, 1, DEGRADED_BY_TIMEOUT)));
        invokers.add(new MockInvoker(1, createCoverage(4900, 49845, 49845, 1, 1, DEGRADED_BY_TIMEOUT)));
        SearchInvoker invoker = createInterleavedInvoker(cluster, 0);

        expectedEvents.add(new Event(null, 100, 0));
        expectedEvents.add(new Event(null, 200, 1));

        Result result = invoker.search(query, null, null);

        Coverage cov = result.getCoverage(true);
        assertThat(cov.getDocs(), is(9900L));
        assertThat(cov.getNodes(), is(2));
        assertThat(cov.getFull(), is(false));
        assertThat(cov.getResultPercentage(), is(10));
        assertThat(cov.getResultSets(), is(1));
        assertThat(cov.getFullResultSets(), is(0));
        assertThat(cov.isDegradedByTimeout(), is(true));
    }

    @Test
    public void requireCorrectCoverageCalculationWhenOneNodeIsUnexpectedlyDown() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", 1, 2);
        invokers.add(new MockInvoker(0, createCoverage(50155, 50155, 50155, 1, 1, 0)));
        invokers.add(new MockInvoker(1, createCoverage(49845, 49845, 49845, 1, 1, 0)));
        SearchInvoker invoker = createInterleavedInvoker(cluster, 0);

        expectedEvents.add(new Event(null, 100, 0));
        expectedEvents.add(null);

        Result result = invoker.search(query, null, null);

        Coverage cov = result.getCoverage(true);
        assertThat(cov.getDocs(), is(50155L));
        assertThat(cov.getNodes(), is(1));
        assertThat(cov.getNodesTried(), is(2));
        assertThat(cov.getFull(), is(false));
        assertThat(cov.getResultPercentage(), is(50));
        assertThat(cov.getResultSets(), is(1));
        assertThat(cov.getFullResultSets(), is(0));
        assertThat(cov.isDegradedByTimeout(), is(true));
    }

    @Test
    public void requireCorrectCoverageCalculationWhenDegradedCoverageIsExpected() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", 1, 2);
        invokers.add(new MockInvoker(0, createCoverage(50155, 50155, 50155, 1, 1, 0)));
        Coverage errorCoverage = new Coverage(0, 0, 0);
        errorCoverage.setNodesTried(1);
        invokers.add(new SearchErrorInvoker(ErrorMessage.createBackendCommunicationError("node is down"), errorCoverage));
        SearchInvoker invoker = createInterleavedInvoker(cluster, 0);

        expectedEvents.add(new Event(null,   1, 1));
        expectedEvents.add(new Event(null, 100, 0));

        Result result = invoker.search(query, null, null);

        Coverage cov = result.getCoverage(true);
        assertThat(cov.getDocs(), is(50155L));
        assertThat(cov.getNodes(), is(1));
        assertThat(cov.getNodesTried(), is(2));
        assertThat(cov.getFull(), is(false));
        assertThat(cov.getResultPercentage(), is(50));
        assertThat(cov.getResultSets(), is(1));
        assertThat(cov.getFullResultSets(), is(0));
        assertThat(cov.isDegradedByTimeout(), is(true));
    }

    private InterleavedSearchInvoker createInterleavedInvoker(SearchCluster searchCluster, int numInvokers) {
        for (int i = 0; i < numInvokers; i++) {
            invokers.add(new MockInvoker(i));
        }

        return new InterleavedSearchInvoker(invokers, null, searchCluster, null) {
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

    private static Coverage createCoverage(int docs, int activeDocs, int soonActiveDocs, int nodes, int nodesTried, int degradedReason) {
        Coverage coverage = new Coverage(docs, activeDocs, nodes);
        coverage.setSoonActive(soonActiveDocs);
        coverage.setNodesTried(nodesTried);
        coverage.setDegradedReason(degradedReason);
        return coverage;
    }

    private static Optional<String> findTrace(Result result, String prefix) {
        var strings = result.getQuery().getContext(false).getTrace().traceNode().descendants(String.class).spliterator();
        return StreamSupport.stream(strings, false).filter(s -> s.startsWith(prefix)).findFirst();
    }

    private class Event {
        Long expectedTimeout;
        long delay;
        Integer invokerIndex;

        public Event(Integer expectedTimeout, int delay, Integer invokerIndex) {
            if (expectedTimeout != null) {
                this.expectedTimeout = (long) expectedTimeout;
            }
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

    public class TestQuery extends Query {
        private long start = clock.millis();

        public TestQuery() {
            super();
            setTimeout(5000);
            setTraceLevel(5);
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
