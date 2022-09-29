// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.concurrent.Timer;
import com.yahoo.document.GlobalId;
import com.yahoo.document.idstring.IdString;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.searchlib.aggregation.MaxAggregationResult;
import com.yahoo.searchlib.aggregation.MinAggregationResult;
import com.yahoo.searchlib.expression.IntegerResultNode;
import com.yahoo.searchlib.expression.StringResultNode;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.search.DispatchConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static com.yahoo.container.handler.Coverage.DEGRADED_BY_MATCH_PHASE;
import static com.yahoo.container.handler.Coverage.DEGRADED_BY_TIMEOUT;
import static com.yahoo.search.dispatch.MockSearchCluster.createDispatchConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ollivir
 */
public class InterleavedSearchInvokerTest {

    private final ManualClock clock = new ManualClock(Instant.now());
    private final Query query = new TestQuery();
    private final LinkedList<Event> expectedEvents = new LinkedList<>();
    private final List<SearchInvoker> invokers = new ArrayList<>();

    @Test
    void requireThatAdaptiveTimeoutsAreNotUsedWithFullCoverageRequirement() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", createDispatchConfig(100.0), 1, 3);
        try (SearchInvoker invoker = createInterleavedInvoker(cluster, new Group(0, List.of()), 3)) {

            expectedEvents.add(new Event(5000, 100, 0));
            expectedEvents.add(new Event(4900, 100, 1));
            expectedEvents.add(new Event(4800, 100, 2));

            invoker.search(query, null);

            assertTrue(expectedEvents.isEmpty(), "All test scenario events processed");
        }
    }

    @Test
    void requireThatTimeoutsAreNotMarkedAsAdaptive() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", createDispatchConfig(100.0), 1, 3);
        try (SearchInvoker invoker = createInterleavedInvoker(cluster, new Group(0, List.of()), 3)) {

            expectedEvents.add(new Event(5000, 300, 0));
            expectedEvents.add(new Event(4700, 300, 1));
            expectedEvents.add(null);

            Result result = invoker.search(query, null);

            assertTrue(expectedEvents.isEmpty(), "All test scenario events processed");
            assertNull(result.hits().getErrorHit(), "Result is not marked as an error");
            var message = findTrace(result, "Backend communication timeout");
            assertTrue(message.isPresent(), "Timeout should be reported in a trace message");
            assertTrue(result.getCoverage(false).isDegradedByTimeout(), "Degradation reason is a normal timeout");
        }
    }

    @Test
    void requireThatAdaptiveTimeoutDecreasesTimeoutWhenCoverageIsReached() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", createDispatchConfig(50.0), 1, 4);
        try (SearchInvoker invoker = createInterleavedInvoker(cluster, new Group(0, List.of()), 4)) {

            expectedEvents.add(new Event(5000, 100, 0));
            expectedEvents.add(new Event(4900, 100, 1));
            expectedEvents.add(new Event(2400, 100, 2));
            expectedEvents.add(new Event(0, 0, null));

            Result result = invoker.search(query, null);

            assertTrue(expectedEvents.isEmpty(), "All test scenario events processed");
            assertNull(result.hits().getErrorHit(), "Result is not marked as an error");
            var message = findTrace(result, "Backend communication timeout");
            assertTrue(message.isPresent(), "Timeout should be reported in a trace message");
            assertTrue(result.getCoverage(false).isDegradedByAdapativeTimeout(), "Degradataion reason is an adaptive timeout");
        }
    }

    @Test
    void requireCorrectCoverageCalculationWhenAllNodesOk() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", 1, 2);
        invokers.add(new MockInvoker(0, createCoverage(50155, 50155, 50155, 1, 1, 0)));
        invokers.add(new MockInvoker(1, createCoverage(49845, 49845, 49845, 1, 1, 0)));
        try (SearchInvoker invoker = createInterleavedInvoker(cluster, new Group(0, List.of()), 0)) {

            expectedEvents.add(new Event(null, 100, 0));
            expectedEvents.add(new Event(null, 200, 1));

            Result result = invoker.search(query, null);

            Coverage cov = result.getCoverage(true);
            assertEquals(100000L, cov.getDocs());
            assertEquals(2, cov.getNodes());
            assertTrue(cov.getFull());
            assertEquals(100, cov.getResultPercentage());
            assertEquals(1, cov.getResultSets());
            assertEquals(1, cov.getFullResultSets());
        }
    }

    @Test
    void requireCorrectCoverageCalculationWhenResultsAreLimitedByMatchPhase() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", 1, 2);
        invokers.add(new MockInvoker(0, createCoverage(10101, 50155, 50155, 1, 1, DEGRADED_BY_MATCH_PHASE)));
        invokers.add(new MockInvoker(1, createCoverage(13319, 49845, 49845, 1, 1, DEGRADED_BY_MATCH_PHASE)));
        try (SearchInvoker invoker = createInterleavedInvoker(cluster, new Group(0, List.of()), 0)) {

            expectedEvents.add(new Event(null, 100, 0));
            expectedEvents.add(new Event(null, 200, 1));

            Result result = invoker.search(query, null);

            Coverage cov = result.getCoverage(true);
            assertEquals(23420L, cov.getDocs());
            assertEquals(2, cov.getNodes());
            assertFalse(cov.getFull());
            assertEquals(23, cov.getResultPercentage());
            assertEquals(1, cov.getResultSets());
            assertEquals(0, cov.getFullResultSets());
            assertTrue(cov.isDegradedByMatchPhase());
        }
    }

    @Test
    void requireCorrectCoverageCalculationWhenResultsAreLimitedBySoftTimeout() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", 1, 2);
        invokers.add(new MockInvoker(0, createCoverage(5000, 50155, 50155, 1, 1, DEGRADED_BY_TIMEOUT)));
        invokers.add(new MockInvoker(1, createCoverage(4900, 49845, 49845, 1, 1, DEGRADED_BY_TIMEOUT)));
        try (SearchInvoker invoker = createInterleavedInvoker(cluster, new Group(0, List.of()), 0)) {

            expectedEvents.add(new Event(null, 100, 0));
            expectedEvents.add(new Event(null, 200, 1));

            Result result = invoker.search(query, null);

            Coverage cov = result.getCoverage(true);
            assertEquals(9900L, cov.getDocs());
            assertEquals(2, cov.getNodes());
            assertFalse(cov.getFull());
            assertEquals(10, cov.getResultPercentage());
            assertEquals(1, cov.getResultSets());
            assertEquals(0, cov.getFullResultSets());
            assertTrue(cov.isDegradedByTimeout());
        }
    }

    @Test
    void requireCorrectCoverageCalculationWhenOneNodeIsUnexpectedlyDown() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", 1, 2);
        invokers.add(new MockInvoker(0, createCoverage(50155, 50155, 50155, 1, 1, 0)));
        invokers.add(new MockInvoker(1, createCoverage(49845, 49845, 49845, 1, 1, 0)));
        try (SearchInvoker invoker = createInterleavedInvoker(cluster, new Group(0, List.of()), 0)) {

            expectedEvents.add(new Event(null, 100, 0));
            expectedEvents.add(null);

            Result result = invoker.search(query, null);

            Coverage cov = result.getCoverage(true);
            assertEquals(50155L, cov.getDocs());
            assertEquals(1, cov.getNodes());
            assertEquals(2, cov.getNodesTried());
            assertFalse(cov.getFull());
            assertEquals(50, cov.getResultPercentage());
            assertEquals(1, cov.getResultSets());
            assertEquals(0, cov.getFullResultSets());
            assertTrue(cov.isDegradedByTimeout());
        }
    }

    static class MetaHit extends Hit {
        MetaHit(Double relevance) {
            super(new Relevance(relevance));
        }
        @Override
        public boolean isMeta() {
            return true;
        }
    }

    private static final double DELTA = 0.000000000001;
    private static final List<Double> A5 = Arrays.asList(11.0,8.5,7.5,3.0,2.0);
    private static final List<Double> B5 = Arrays.asList(9.0,8.0,7.0,6.0,1.0);
    private static final List<Double> A5Aux = Arrays.asList(-1.0,11.0,8.5,7.5,-7.0,3.0,2.0);
    private static final List<Double> B5Aux = Arrays.asList(9.0,8.0,-3.0,7.0,6.0,1.0, -1.0);

    private void validateThatTopKProbabilityOverrideTakesEffect(Double topKProbability, int expectedK, Group group) throws IOException {
        try (InterleavedSearchInvoker invoker = createInterLeavedTestInvoker(A5, B5, group)) {
            query.setHits(8);
            query.properties().set(Dispatcher.topKProbability, topKProbability);
            SearchInvoker[] invokers = invoker.invokers().toArray(new SearchInvoker[0]);
            Result result = invoker.search(query, null);
            assertEquals(2, invokers.length);
            assertEquals(expectedK, ((MockInvoker) invokers[0]).hitsRequested);
            assertEquals(8, result.hits().size());
            assertEquals(11.0, result.hits().get(0).getRelevance().getScore(), DELTA);
            assertEquals(9.0, result.hits().get(1).getRelevance().getScore(), DELTA);
            assertEquals(8.5, result.hits().get(2).getRelevance().getScore(), DELTA);
            assertEquals(8.0, result.hits().get(3).getRelevance().getScore(), DELTA);
            assertEquals(7.5, result.hits().get(4).getRelevance().getScore(), DELTA);
            assertEquals(7.0, result.hits().get(5).getRelevance().getScore(), DELTA);
            assertEquals(6.0, result.hits().get(6).getRelevance().getScore(), DELTA);
            assertEquals(3.0, result.hits().get(7).getRelevance().getScore(), DELTA);
            assertEquals(0, result.getQuery().getOffset());
            assertEquals(8, result.getQuery().getHits());
        }
    }

    @Test
    void requireThatTopKProbabilityOverrideTakesEffect() throws IOException {
        validateThatTopKProbabilityOverrideTakesEffect(null, 8, new Group(0, List.of()));
        validateThatTopKProbabilityOverrideTakesEffect(0.8, 7, new Group(0, List.of()));
    }

    @Test
    void requireThatTopKProbabilityOverrideIsDisabledOnContentSkew() throws IOException {
        Node node0 = new Node(0, "host0", 0);
        Node node1 = new Node(1, "host1", 0);
        Group group = new Group(0, List.of(node0, node1));

        node0.setActiveDocuments(1000000);
        node1.setActiveDocuments(1100000);
        group.aggregateNodeValues();
        validateThatTopKProbabilityOverrideTakesEffect(0.8, 8, group);
    }

    @Test
    void requireThatTopKProbabilityOverrideIsDisabledOnLittleContent() throws IOException {
        Node node0 = new Node(0, "host0", 0);
        Node node1 = new Node(1, "host1", 0);
        Group group = new Group(0, List.of(node0, node1));

        node0.setActiveDocuments(10);
        node1.setActiveDocuments(10);
        group.aggregateNodeValues();
        validateThatTopKProbabilityOverrideTakesEffect(0.8, 8, group);
    }

    @Test
    void requireThatMergeOfConcreteHitsObeySorting() throws IOException {
        try (InterleavedSearchInvoker invoker = createInterLeavedTestInvoker(A5, B5, new Group(0, List.of()))) {
            query.setHits(12);
            Result result = invoker.search(query, null);
            assertEquals(10, result.hits().size());
            assertEquals(11.0, result.hits().get(0).getRelevance().getScore(), DELTA);
            assertEquals(1.0, result.hits().get(9).getRelevance().getScore(), DELTA);
            assertEquals(0, result.getQuery().getOffset());
            assertEquals(12, result.getQuery().getHits());
        }

        try ( InterleavedSearchInvoker invoker = createInterLeavedTestInvoker(B5, A5, new Group(0, List.of()))) {
            Result result = invoker.search(query, null);
            assertEquals(10, result.hits().size());
            assertEquals(11.0, result.hits().get(0).getRelevance().getScore(), DELTA);
            assertEquals(1.0, result.hits().get(9).getRelevance().getScore(), DELTA);
            assertEquals(0, result.getQuery().getOffset());
            assertEquals(12, result.getQuery().getHits());
        }
    }

    @Test
    void requireThatMergeOfConcreteHitsObeyOffset() throws IOException {
        try (InterleavedSearchInvoker invoker = createInterLeavedTestInvoker(A5, B5, new Group(0, List.of()))) {
            query.setHits(3);
            query.setOffset(5);
            Result result = invoker.search(query, null);
            assertEquals(3, result.hits().size());
            assertEquals(7.0, result.hits().get(0).getRelevance().getScore(), DELTA);
            assertEquals(3.0, result.hits().get(2).getRelevance().getScore(), DELTA);
            assertEquals(0, result.getQuery().getOffset());
            assertEquals(3, result.getQuery().getHits());
        }

        try (InterleavedSearchInvoker invoker = createInterLeavedTestInvoker(B5, A5, new Group(0, List.of()))) {
            query.setOffset(5);
            Result result = invoker.search(query, null);
            assertEquals(3, result.hits().size());
            assertEquals(7.0, result.hits().get(0).getRelevance().getScore(), DELTA);
            assertEquals(3.0, result.hits().get(2).getRelevance().getScore(), DELTA);
            assertEquals(0, result.getQuery().getOffset());
            assertEquals(3, result.getQuery().getHits());
        }
    }

    @Test
    void requireThatMergeOfConcreteHitsObeyOffsetWithAuxilliaryStuff() throws IOException {
        try (InterleavedSearchInvoker invoker = createInterLeavedTestInvoker(A5Aux, B5Aux, new Group(0, List.of()))) {
            query.setHits(3);
            query.setOffset(5);
            Result result = invoker.search(query, null);
            assertEquals(7, result.hits().size());
            assertEquals(7.0, result.hits().get(0).getRelevance().getScore(), DELTA);
            assertEquals(3.0, result.hits().get(2).getRelevance().getScore(), DELTA);
            assertTrue(result.hits().get(3) instanceof MetaHit);
            assertEquals(0, result.getQuery().getOffset());
            assertEquals(3, result.getQuery().getHits());
        }

        try (InterleavedSearchInvoker invoker = createInterLeavedTestInvoker(B5Aux, A5Aux, new Group(0, List.of()))) {
            query.setOffset(5);
            Result result = invoker.search(query, null);
            assertEquals(7, result.hits().size());
            assertEquals(7.0, result.hits().get(0).getRelevance().getScore(), DELTA);
            assertEquals(3.0, result.hits().get(2).getRelevance().getScore(), DELTA);
            assertTrue(result.hits().get(3) instanceof MetaHit);
            assertEquals(0, result.getQuery().getOffset());
            assertEquals(3, result.getQuery().getHits());
        }
    }

    @Test
    void requireThatGroupingsAreMerged() throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", 1, 2);
        List<SearchInvoker> invokers = new ArrayList<>();

        Grouping grouping1 = new Grouping(0);
        grouping1.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group()
                        .setId(new StringResultNode("uniqueA"))
                        .addAggregationResult(new MaxAggregationResult().setMax(new IntegerResultNode(6)).setTag(4)))
                .addChild(new com.yahoo.searchlib.aggregation.Group()
                        .setId(new StringResultNode("common"))
                        .addAggregationResult(new MaxAggregationResult().setMax(new IntegerResultNode(9)).setTag(4))));
        invokers.add(new MockInvoker(0).setHits(List.of(new GroupingListHit(List.of(grouping1)))));

        Grouping grouping2 = new Grouping(0);
        grouping2.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group()
                        .setId(new StringResultNode("uniqueB"))
                        .addAggregationResult(new MaxAggregationResult().setMax(new IntegerResultNode(9)).setTag(4)))
                .addChild(new com.yahoo.searchlib.aggregation.Group()
                        .setId(new StringResultNode("common"))
                        .addAggregationResult(new MinAggregationResult().setMin(new IntegerResultNode(6)).setTag(3))));
        invokers.add(new MockInvoker(0).setHits(List.of(new GroupingListHit(List.of(grouping2)))));

        try (InterleavedSearchInvoker invoker = new InterleavedSearchInvoker(Timer.monotonic, invokers, cluster, new Group(0, List.of()), Collections.emptySet())) {
            invoker.responseAvailable(invokers.get(0));
            invoker.responseAvailable(invokers.get(1));
            Result result = invoker.search(query, null);
            assertEquals(1, ((GroupingListHit) result.hits().get(0)).getGroupingList().size());
        }
        for (SearchInvoker invoker : invokers) {
            invoker.close();
        }
    }

    private static InterleavedSearchInvoker createInterLeavedTestInvoker(List<Double> a, List<Double> b, Group group) {
        SearchCluster cluster = new MockSearchCluster("!", 1, 2);
        List<SearchInvoker> invokers = new ArrayList<>();
        invokers.add(createInvoker(a, 0));
        invokers.add(createInvoker(b, 1));
        InterleavedSearchInvoker invoker = new InterleavedSearchInvoker(Timer.monotonic, invokers, cluster, group, Collections.emptySet());
        invoker.responseAvailable(invokers.get(0));
        invoker.responseAvailable(invokers.get(1));
        return invoker;
    }
    private static MockInvoker createInvoker(List<Double> scores, int distributionKey) {
        return new MockInvoker(0).setHits(createHits(scores, distributionKey, distributionKey));
    }

    private static List<Hit> createHits(List<Double> scores, int partId, int distributionKey) {
        List<Hit> hits= new ArrayList<>(scores.size());
        for (Double value : scores) {
            if (value < 0) {
                hits.add(new MetaHit(value));
            } else {
                hits.add(new FastHit(new GlobalId(IdString.createIdString("id:test:test::" + value)).getRawId(), new Relevance(value), partId, distributionKey));
            }
        }
        return hits;
    }

    void verifyCorrectCoverageCalculationWhenDegradedCoverageIsExpected(DispatchConfig dispatchConfig, int expectedCoverage) throws IOException {
        SearchCluster cluster = new MockSearchCluster("!", dispatchConfig, 1, 2);
        invokers.add(new MockInvoker(0, createCoverage(50155, 50155, 60000, 1, 1, 0)));
        Coverage errorCoverage = new Coverage(0, 0, 0);
        errorCoverage.setNodesTried(1);
        invokers.add(new SearchErrorInvoker(ErrorMessage.createBackendCommunicationError("node is down"), errorCoverage));
        try (SearchInvoker invoker = createInterleavedInvoker(cluster, new Group(0, List.of()), 0)) {

            expectedEvents.add(new Event(null, 1, 1));
            expectedEvents.add(new Event(null, 100, 0));

            Result result = invoker.search(query, null);

            Coverage cov = result.getCoverage(true);
            assertEquals(50155L, cov.getDocs());
            assertEquals(1, cov.getNodes());
            assertEquals(2, cov.getNodesTried());
            assertFalse(cov.getFull());
            assertEquals(expectedCoverage, cov.getResultPercentage());
            assertEquals(1, cov.getResultSets());
            assertEquals(0, cov.getFullResultSets());
            assertTrue(cov.isDegradedByTimeout());
        }
    }

    @Test
    void requireCorrectCoverageCalculationWhenDegradedCoverageIsExpectedUsingTargetActiveDocs() throws IOException {
        verifyCorrectCoverageCalculationWhenDegradedCoverageIsExpected(MockSearchCluster.createDispatchConfig(100.0, List.of())
                .searchableCopies(1)
                .redundancy(1)
                .build(),
                42);
    }

    private InterleavedSearchInvoker createInterleavedInvoker(SearchCluster searchCluster, Group group, int numInvokers) {
        for (int i = 0; i < numInvokers; i++) {
            invokers.add(new MockInvoker(i));
        }

        return new InterleavedSearchInvoker(Timer.wrap(clock), invokers, searchCluster, group,null) {

            @Override
            protected LinkedBlockingQueue<SearchInvoker> newQueue() {
                return new LinkedBlockingQueue<>() {
                    @Override
                    public SearchInvoker poll(long timeout, TimeUnit timeUnit) {
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

    private static Coverage createCoverage(int docs, int activeDocs, int targetActiveDocs, int nodes, int nodesTried, int degradedReason) {
        Coverage coverage = new Coverage(docs, activeDocs, nodes);
        coverage.setTargetActive(targetActiveDocs);
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
                assertEquals((long) expectedTimeout, currentTimeout, "Expecting timeout to be " + expectedTimeout);
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
        private final long start = clock.millis();

        public TestQuery() {
            super();
            setTimeout(5000);
            getTrace().setLevel(5);
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
