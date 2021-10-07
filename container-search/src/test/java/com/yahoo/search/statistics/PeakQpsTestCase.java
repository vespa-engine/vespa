// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.statistics;

import static org.junit.Assert.*;

import java.util.Deque;
import java.util.List;

import com.yahoo.statistics.Statistics;
import org.junit.Test;

import com.yahoo.component.chain.Chain;
import com.yahoo.concurrent.LocalInstance;
import com.yahoo.concurrent.ThreadLocalDirectory;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.statistics.PeakQpsSearcher.QueryRatePerSecond;

/**
 * Check peak QPS aggregation has a chance of working.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class PeakQpsTestCase {

    static class Producer implements Runnable {
        private final ThreadLocalDirectory<Deque<QueryRatePerSecond>, Long> rates;

        Producer(ThreadLocalDirectory<Deque<QueryRatePerSecond>, Long> rates) {
            this.rates = rates;
        }

        @Override
        public void run() {
            LocalInstance<Deque<QueryRatePerSecond>, Long> rate = rates.getLocalInstance();
            rates.update(1L, rate);
            rates.update(2L, rate);
            rates.update(2L, rate);
            rates.update(3L, rate);
            rates.update(3L, rate);
            rates.update(3L, rate);
            rates.update(4L, rate);
            rates.update(4L, rate);
            rates.update(4L, rate);
            rates.update(4L, rate);
        }
    }

    static class LaterProducer implements Runnable {
        private final ThreadLocalDirectory<Deque<QueryRatePerSecond>, Long> rates;

        LaterProducer(ThreadLocalDirectory<Deque<QueryRatePerSecond>, Long> rates) {
            this.rates = rates;
        }

        @Override
        public void run() {
            LocalInstance<Deque<QueryRatePerSecond>, Long> rate = rates.getLocalInstance();
            rates.update(2L, rate);
            rates.update(2L, rate);
            rates.update(3L, rate);
            rates.update(3L, rate);
            rates.update(3L, rate);
            rates.update(5L, rate);
            rates.update(5L, rate);
            rates.update(6L, rate);
            rates.update(7L, rate);
        }
    }

    @Test
    public void checkBasicDataAggregation() {
        ThreadLocalDirectory<Deque<QueryRatePerSecond>, Long> directory = PeakQpsSearcher.createDirectory();
        final int threadCount = 20;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; ++i) {
            Producer p = new Producer(directory);
            threads[i] = new Thread(p);
            threads[i].start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                // nop
            }
        }
        List<Deque<QueryRatePerSecond>> measurements = directory.fetch();
        List<QueryRatePerSecond> results = PeakQpsSearcher.merge(measurements);
        assertTrue(results.get(0).when == 1L);
        assertTrue(results.get(0).howMany == threadCount);
        assertTrue(results.get(1).when == 2L);
        assertTrue(results.get(1).howMany == threadCount * 2);
        assertTrue(results.get(2).when == 3L);
        assertTrue(results.get(2).howMany == threadCount * 3);
        assertTrue(results.get(3).when == 4L);
        assertTrue(results.get(3).howMany == threadCount * 4);
    }

    @Test
    public void checkMixedDataAggregation() {
        ThreadLocalDirectory<Deque<QueryRatePerSecond>, Long> directory = PeakQpsSearcher.createDirectory();
        final int firstThreads = 20;
        final int secondThreads = 20;
        final int threadCount = firstThreads + secondThreads;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; ++i) {
            if (i < firstThreads) {
                Producer p = new Producer(directory);
                threads[i] = new Thread(p);
            } else {
                LaterProducer p = new LaterProducer(directory);
                threads[i] = new Thread(p);
            }
            threads[i].start();

        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                // nop
            }
        }
        List<Deque<QueryRatePerSecond>> measurements = directory.fetch();
        List<QueryRatePerSecond> results = PeakQpsSearcher.merge(measurements);
        assertTrue(results.size() == 7);
        assertTrue(results.get(0).when == 1L);
        assertTrue(results.get(0).howMany == firstThreads);
        assertTrue(results.get(1).when == 2L);
        assertTrue(results.get(1).howMany == threadCount * 2);
        assertTrue(results.get(2).when == 3L);
        assertTrue(results.get(2).howMany == threadCount * 3);
        assertTrue(results.get(3).when == 4L);
        assertTrue(results.get(3).howMany == firstThreads * 4);
        assertTrue(results.get(4).when == 5L);
        assertTrue(results.get(4).howMany == secondThreads * 2);
        assertTrue(results.get(5).when == 6L);
        assertTrue(results.get(5).howMany == secondThreads);
        assertTrue(results.get(6).when == 7L);
        assertTrue(results.get(6).howMany == secondThreads);
    }

    @Test
    public void checkSearch() {
        MeasureQpsConfig config = new MeasureQpsConfig(
                new MeasureQpsConfig.Builder().outputmethod(
                        MeasureQpsConfig.Outputmethod.METAHIT).queryproperty(
                        "qpsprobe"));
        Searcher s = new PeakQpsSearcher(config, Statistics.nullImplementation);
        Chain<Searcher> c = new Chain<>(s);
        Execution e = new Execution(c, Execution.Context.createContextStub());
        e.search(new Query("/?query=a"));
        new Execution(c, Execution.Context.createContextStub());
        Result r = e.search(new Query("/?query=a&qpsprobe=true"));
        final Hit hit = r.hits().get(0);
        assertTrue(hit instanceof PeakQpsSearcher.QpsHit);
        assertNotNull(hit.fields().get(PeakQpsSearcher.QpsHit.MEAN_QPS));
        assertNotNull(hit.fields().get(PeakQpsSearcher.QpsHit.PEAK_QPS));
    }
}
