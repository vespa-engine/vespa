// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.statistics;

import junit.framework.TestCase;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.statistics.TimeTracker.Activity;
import com.yahoo.search.statistics.TimeTracker.SearcherTimer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Check sanity of TimeTracker and ElapsedTime.
 *
 * @author Steinar Knutsen
 */
public class ElapsedTimeTestCase {

    private static final long[] SEARCH_TIMESEQUENCE = new long[] { 1L, 2L, 3L, 4L, 5L, 6L, 7L };

    private static final long[] SEARCH_AND_FILL_TIMESEQUENCE = new long[] { 1L, 2L, 3L, 4L, 5L, 6L, 7L,
                                                                            // and here we start filling
                                                                            7L, 8L, 9L, 10L, 11L, 12L, 13L };

    public static class CreativeTimeSource extends TimeTracker.TimeSource {
        private int nowIndex = 0;
        private long[] now;

        public CreativeTimeSource(long[] now) {
            this.now = now;
        }

        @Override
        long now() {
            long present = now[nowIndex++];
            if (present == 0L) {
                // defensive coding against the innards of TimeTracker
                throw new IllegalStateException("0 is an unsupported time stamp value.");
            }
            return present;
        }

    }

    public static class UselessSearcher extends Searcher {
        public UselessSearcher(String name) {
            super(new ComponentId(name));
        }

        @Override
        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }
    }

    private static class AlmostUselessSearcher extends Searcher {
        AlmostUselessSearcher(String name) {
            super(new ComponentId(name));
        }

        @Override
        public Result search(Query query, Execution execution) {
            Result r = execution.search(query);
            Hit h = new Hit("nalle");
            h.setFillable();
            r.hits().add(h);
            return r;
        }
    }

    private static class NoForwardSearcher extends Searcher {
        @Override
        public Result search(Query query, Execution execution) {
            Result r = new Result(query);
            Hit h = new Hit("nalle");
            h.setFillable();
            r.hits().add(h);
            return r;
        }
    }

    private class TestingSearcher extends Searcher {
        @Override
        public Result search(Query query, Execution execution) {
            Execution exec = new Execution(execution);
            exec.timer().injectTimeSource(
                    new CreativeTimeSource(SEARCH_TIMESEQUENCE));
            exec.context().setDetailedDiagnostics(true);
            Result r = exec.search(new Query());
            SearcherTimer[] searchers = exec.timer().searcherTracking();
            assertNull(searchers[0].getInvoking(Activity.SEARCH));
            checkTiming(searchers, 1);
            return r;
        }
    }

    private class SecondTestingSearcher extends Searcher {
        @Override
        public Result search(Query query, Execution execution) {
            Execution exec = new Execution(execution);
            exec.timer().injectTimeSource(
                    new CreativeTimeSource(SEARCH_AND_FILL_TIMESEQUENCE));
            exec.context().setDetailedDiagnostics(true);
            Result result = exec.search(new Query());
            exec.fill(result);
            SearcherTimer[] searchers = exec.timer().searcherTracking();
            assertNull(searchers[0].getInvoking(Activity.SEARCH));
            checkTiming(searchers, 1);
            assertNull(searchers[0].getInvoking(Activity.FILL));
            checkFillTiming(searchers, 1);
            return result;
        }
    }

    private class ShortChainTestingSearcher extends Searcher {
        @Override
        public Result search(Query query, Execution execution) {
            Execution exec = new Execution(execution);
            exec.timer().injectTimeSource(
                    new CreativeTimeSource(new long[] { 1L, 2L, 2L }));
            exec.context().setDetailedDiagnostics(true);
            Result result = exec.search(new Query());
            SearcherTimer[] searchers = exec.timer().searcherTracking();
            assertNull(searchers[0].getInvoking(Activity.SEARCH));
            assertEquals(Long.valueOf(1L), searchers[1].getInvoking(Activity.SEARCH));
            assertNull(searchers[1].getReturning(Activity.SEARCH));
            assertNull(searchers[0].getInvoking(Activity.FILL));
            assertNull(searchers[1].getInvoking(Activity.FILL));
            assertTrue(0 < result.getElapsedTime().detailedReport().indexOf("NoForwardSearcher"));
            return result;
        }
    }

    @Test
    public void testBasic() {
        TimeTracker t = new TimeTracker(null);
        t.injectTimeSource(new CreativeTimeSource(new long[] {1L, 2L, 3L, 4L}));
        Query q = new Query();
        Result r = new Result(q);
        t.sampleSearch(0, false);
        t.sampleFill(0, false);
        t.samplePing(0, false);
        t.sampleSearchReturn(0, false, r);
        assertEquals(1L, t.first());
        assertEquals(4L, t.last());
        assertEquals(2L, t.firstFill());
        assertEquals(1L, t.searchTime());
        assertEquals(1L, t.fillTime());
        assertEquals(1L, t.pingTime());
        assertEquals(3L, t.totalTime());
    }

    @Test
    public void testMultiSearchAndPing() {
        TimeTracker t = new TimeTracker(null);
        t.injectTimeSource(new CreativeTimeSource(new long[] {1L, 4L, 16L, 32L, 64L, 128L, 256L}));
        Query q = new Query();
        Result r = new Result(q);
        t.sampleSearch(0, false);
        t.samplePing(0, false);
        t.sampleSearch(0, false);
        t.samplePing(0, false);
        t.sampleSearch(0, false);
        t.sampleFill(0, false);
        t.sampleSearchReturn(0, false, r);
        assertEquals(1L, t.first());
        assertEquals(256L, t.last());
        assertEquals(128L, t.firstFill());
        assertEquals(83L, t.searchTime());
        assertEquals(128L, t.fillTime());
        assertEquals(44L, t.pingTime());
        assertEquals(255L, t.totalTime());
        ElapsedTime e = new ElapsedTime();
        e.add(t);
        e.add(t);
        // multiple adds is supposed to be safe
        assertEquals(255L, t.totalTime());
        TimeTracker tx = new TimeTracker(null);
        tx.injectTimeSource(new CreativeTimeSource(new long[] {1L, 2L, 3L, 4L}));
        Query qx = new Query();
        Result rx = new Result(qx);
        tx.sampleSearch(0, false);
        tx.sampleFill(0, false);
        tx.samplePing(0, false);
        tx.sampleSearchReturn(0, false, rx);
        e.add(tx);
        assertEquals(258L, e.totalTime());
        assertEquals(129L, e.fillTime());
        assertEquals(2L, e.firstFill());
    }

    @Test
    public void testBasicBreakdown() {
        TimeTracker t = new TimeTracker(new Chain<Searcher>(
                new UselessSearcher("first"), new UselessSearcher("second"),
                new UselessSearcher("third")));
        t.injectTimeSource(new CreativeTimeSource(new long[] { 1L, 2L, 3L,
                4L, 5L, 6L, 7L }));
        t.sampleSearch(0, true);
        t.sampleSearch(1, true);
        t.sampleSearch(2, true);
        t.sampleSearch(3, true);
        t.sampleSearchReturn(2, true, null);
        t.sampleSearchReturn(1, true, null);
        t.sampleSearchReturn(0, true, null);
        SearcherTimer[] searchers = t.searcherTracking();
        checkTiming(searchers);
    }

    // This test is to make sure the other tests correctly simulate the call
    // order into the TimeTracker
    @Test
    public void testBasicBreakdownFullyWiredIn() {
        Chain<? extends Searcher> chain = new Chain<Searcher>(
                new UselessSearcher("first"), new UselessSearcher("second"),
                new UselessSearcher("third"));
        Execution exec = new Execution(chain, Execution.Context.createContextStub());
        exec.timer().injectTimeSource(new CreativeTimeSource(SEARCH_TIMESEQUENCE));
        exec.context().setDetailedDiagnostics(true);
        exec.search(new Query());
        SearcherTimer[] searchers = exec.timer().searcherTracking();
        checkTiming(searchers);
    }


    private void checkTiming(SearcherTimer[] searchers) {
        checkTiming(searchers, 0);
    }

    private void checkTiming(SearcherTimer[] searchers, int offset) {
        assertEquals(Long.valueOf(1L), searchers[0 + offset].getInvoking(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[1 + offset].getInvoking(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[2 + offset].getInvoking(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[2 + offset].getReturning(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[1 + offset].getReturning(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[0 + offset].getReturning(Activity.SEARCH));
    }

    @Test
    public void testBasicBreakdownWithFillFullyWiredIn() {
        Chain<? extends Searcher> chain = new Chain<>(
                new UselessSearcher("first"), new UselessSearcher("second"),
                new AlmostUselessSearcher("third"));
        Execution exec = new Execution(chain, Execution.Context.createContextStub());
        exec.timer().injectTimeSource(
                new CreativeTimeSource(SEARCH_AND_FILL_TIMESEQUENCE));
        exec.context().setDetailedDiagnostics(true);
        Result result = exec.search(new Query());
        exec.fill(result);
        SearcherTimer[] searchers = exec.timer().searcherTracking();
        checkTiming(searchers);
        checkFillTiming(searchers);
    }

    private void checkFillTiming(SearcherTimer[] searchers) {
        checkFillTiming(searchers, 0);
    }

    private void checkFillTiming(SearcherTimer[] searchers, int offset) {
        assertEquals(Long.valueOf(1L), searchers[0 + offset].getInvoking(Activity.FILL));
        assertEquals(Long.valueOf(1L), searchers[1 + offset].getInvoking(Activity.FILL));
        assertEquals(Long.valueOf(1L), searchers[2 + offset].getInvoking(Activity.FILL));
        assertEquals(Long.valueOf(1L), searchers[2 + offset].getReturning(Activity.FILL));
        assertEquals(Long.valueOf(1L), searchers[1 + offset].getReturning(Activity.FILL));
        assertEquals(Long.valueOf(1L), searchers[0 + offset].getReturning(Activity.FILL));
    }

    @Test
    public void testBasicBreakdownFullyWiredInFirstSearcherNotFirstInChain() {
        Chain<? extends Searcher> chain = new Chain<>(
                new TestingSearcher(),
                new UselessSearcher("first"), new UselessSearcher("second"),
                new UselessSearcher("third"));
        Execution exec = new Execution(chain, Execution.Context.createContextStub());
        exec.search(new Query());
    }

    @Test
    public void testBasicBreakdownWithFillFullyWiredInFirstSearcherNotFirstInChain() {
        Chain<? extends Searcher> chain = new Chain<>(
                new SecondTestingSearcher(),
                new UselessSearcher("first"), new UselessSearcher("second"),
                new AlmostUselessSearcher("third"));
        Execution exec = new Execution(chain, Execution.Context.createContextStub());
        exec.search(new Query());
    }

    @Test
    public void testTimingWithShortChain() {
        Chain<? extends Searcher> chain = new Chain<>(
                new ShortChainTestingSearcher(),
                new NoForwardSearcher());
        Execution exec = new Execution(chain, Execution.Context.createContextStub());
        exec.search(new Query());
    }

    @Test
    public void testBasicBreakdownReturnInsideSearchChain() {
        TimeTracker t = new TimeTracker(new Chain<Searcher>(
                new UselessSearcher("first"), new UselessSearcher("second"),
                new UselessSearcher("third")));
        t.injectTimeSource(new CreativeTimeSource(new long[] { 1L, 2L, 3L,
                4L, 5L, 6L }));
        t.sampleSearch(0, true);
        t.sampleSearch(1, true);
        t.sampleSearch(2, true);
        t.sampleSearchReturn(2, true, null);
        t.sampleSearchReturn(1, true, null);
        t.sampleSearchReturn(0, true, null);
        SearcherTimer[] searchers = t.searcherTracking();
        assertEquals(Long.valueOf(1L), searchers[0].getInvoking(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[1].getInvoking(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[2].getInvoking(Activity.SEARCH));
        assertNull(searchers[2].getReturning(Activity.SEARCH));
        assertEquals(Long.valueOf(1L) ,searchers[1].getReturning(Activity.SEARCH));
        assertEquals(Long.valueOf(1L) ,searchers[0].getReturning(Activity.SEARCH));
    }

    @Test
    public void testBasicBreakdownWithFill() {
        TimeTracker t = new TimeTracker(new Chain<Searcher>(
                new UselessSearcher("first"), new UselessSearcher("second"),
                new UselessSearcher("third")));
        t.injectTimeSource(new CreativeTimeSource(new long[] { 1L, 2L, 3L,
                4L, 5L, 6L, 7L, 7L, 8L, 9L, 10L}));
        t.sampleSearch(0, true);
        t.sampleSearch(1, true);
        t.sampleSearch(2, true);
        t.sampleSearch(3, true);
        t.sampleSearchReturn(2, true, null);
        t.sampleSearchReturn(1, true, null);
        t.sampleSearchReturn(0, true, null);
        t.sampleFill(0, true);
        t.sampleFill(1, true);
        t.sampleFillReturn(1, true, null);
        t.sampleFillReturn(0, true, null);
        SearcherTimer[] searchers = t.searcherTracking();
        assertEquals(Long.valueOf(1L), searchers[0].getInvoking(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[1].getInvoking(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[2].getInvoking(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[2].getReturning(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[1].getReturning(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[0].getReturning(Activity.SEARCH));
        assertEquals(Long.valueOf(1L), searchers[0].getInvoking(Activity.FILL));
        assertEquals(Long.valueOf(1L), searchers[1].getInvoking(Activity.FILL));
        assertNull(searchers[1].getReturning(Activity.FILL));
        assertEquals(Long.valueOf(1L), searchers[0].getReturning(Activity.FILL));
    }

    private void runSomeTraffic(TimeTracker t) {
        t.injectTimeSource(new CreativeTimeSource(new long[] {
                1L, 2L, 3L,
                // checkpoint 1
                4L, 5L,
                // checkpoint 2
                6L, 7L, 8L, 9L,
                // checkpoint 3
                10L, 11L, 12L, 13L,
                // checkpoint 4
                14L, 15L, 16L, 17L,
                // checkpoint 5
                18L
                }));
        t.sampleSearch(0, true);
        t.sampleSearch(1, true);
        t.sampleSearch(2, true);
        // checkpoint 1
        t.sampleSearchReturn(2, true, null);
        t.sampleSearchReturn(1, true, null);
        // checkpoint 2
        t.sampleFill(1, true);
        t.sampleFill(2, true);
        t.sampleFillReturn(2, true, null);
        t.sampleFillReturn(1, true, null);
        // checkpoint 3
        t.sampleSearch(1, true);
        t.sampleSearch(2, true);
        t.sampleSearchReturn(2, true, null);
        t.sampleSearchReturn(1, true, null);
        // checkpoint 4
        t.sampleFill(1, true);
        t.sampleFill(2, true);
        t.sampleFillReturn(2, true, null);
        t.sampleFillReturn(1, true, null);
        // checkpoint 5
        t.sampleSearchReturn(0, true, null);
    }

    @Test
    public void testMixedActivity() {
        TimeTracker t = new TimeTracker(new Chain<Searcher>(
                new UselessSearcher("first"), new UselessSearcher("second"),
                new UselessSearcher("third")));
        runSomeTraffic(t);

        SearcherTimer[] searchers = t.searcherTracking();
        assertEquals(Long.valueOf(1L), searchers[0].getInvoking(Activity.SEARCH));
        assertNull(searchers[0].getInvoking(Activity.FILL));
        assertEquals(Long.valueOf(2L), searchers[0].getReturning(Activity.SEARCH));
        assertEquals(Long.valueOf(2L), searchers[0].getReturning(Activity.FILL));

        assertEquals(Long.valueOf(2L), searchers[1].getInvoking(Activity.SEARCH));
        assertEquals(Long.valueOf(2L), searchers[1].getInvoking(Activity.FILL));
        assertEquals(Long.valueOf(2L), searchers[1].getReturning(Activity.SEARCH));
        assertEquals(Long.valueOf(2L), searchers[1].getReturning(Activity.FILL));

        assertEquals(Long.valueOf(2L), searchers[2].getInvoking(Activity.SEARCH));
        assertEquals(Long.valueOf(2L), searchers[2].getInvoking(Activity.FILL));
        assertNull(searchers[2].getReturning(Activity.SEARCH));
        assertNull(searchers[2].getReturning(Activity.FILL));
    }

    @Test
    public void testReportGeneration() {
        TimeTracker t = new TimeTracker(new Chain<Searcher>(
                new UselessSearcher("first"), new UselessSearcher("second"),
                new UselessSearcher("third")));
        runSomeTraffic(t);

        ElapsedTime elapsed = new ElapsedTime();
        elapsed.add(t);
        t = new TimeTracker(new Chain<Searcher>(
                new UselessSearcher("first"), new UselessSearcher("second"),
                new UselessSearcher("third")));
        runSomeTraffic(t);
        elapsed.add(t);
        assertEquals(true, elapsed.hasDetailedData());
        assertEquals("Time use per searcher:"
                + " first(QueryProcessing(SEARCH: 2 ms), ResultProcessing(SEARCH: 4 ms, FILL: 4 ms)),\n"
                + "    second(QueryProcessing(SEARCH: 4 ms, FILL: 4 ms), ResultProcessing(SEARCH: 4 ms, FILL: 4 ms)),\n"
                + "    third(QueryProcessing(SEARCH: 4 ms, FILL: 4 ms), ResultProcessing()).",
                elapsed.detailedReport());
    }

    public static void doInjectTimeSource(TimeTracker t, TimeTracker.TimeSource s) {
        t.injectTimeSource(s);
    }

}
