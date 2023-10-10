// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

/**
 * Smoke test for multi producer data structure.
 *
 * <p>
 * TODO sorely needs nastier cases
 * </p>
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ThreadLocalDirectoryTestCase {
    private static class SumUpdater implements ThreadLocalDirectory.Updater<Integer, Integer> {

        @Override
        public Integer update(Integer current, Integer x) {
            return Integer.valueOf(current.intValue() + x.intValue());
        }

        @Override
        public Integer createGenerationInstance(Integer previous) {
            return Integer.valueOf(0);
        }
    }

    private static class ObservableSumUpdater extends SumUpdater implements ThreadLocalDirectory.ObservableUpdater<Integer, Integer> {

        @Override
        public Integer copy(Integer current) {
            return current;
        }
    }


    private static class Counter implements Runnable {
        ThreadLocalDirectory<Integer, Integer> r;

        Counter(ThreadLocalDirectory<Integer, Integer> r) {
            this.r = r;
        }

        @Override
        public void run() {
            LocalInstance<Integer, Integer> s = r.getLocalInstance();
            for (int i = 0; i < 500; ++i) {
                put(s, i);
            }
        }

        void put(LocalInstance<Integer, Integer> s, int i) {
            r.update(Integer.valueOf(i), s);
        }
    }

    private static class CounterAndViewer extends Counter {
        CounterAndViewer(ThreadLocalDirectory<Integer, Integer> r) {
            super(r);
        }

        @Override
        void put(LocalInstance<Integer, Integer> s, int i) {
            super.put(s, i);
            if (i % 10 == 0) {
                r.view();
            }
        }
    }

    @Test
    public void sumFromMultipleThreads() {
        SumUpdater updater = new SumUpdater();
        ThreadLocalDirectory<Integer, Integer> s = new ThreadLocalDirectory<>(updater);
        Thread[] threads = new Thread[500];
        for (int i = 0; i < 500; ++i) {
            Counter c = new Counter(s);
            threads[i] = new Thread(c);
        }
        runAll(threads);
        List<Integer> measurements = s.fetch();
        int sum = 0;
        for (Integer i : measurements) {
            sum += i.intValue();
        }
        assertTrue("Data lost.", 62375000 == sum);
    }

    @Test
    public void sumAndViewFromMultipleThreads() {
        ObservableSumUpdater updater = new ObservableSumUpdater();
        ThreadLocalDirectory<Integer, Integer> s = new ThreadLocalDirectory<>(updater);
        Thread[] threads = new Thread[500];
        for (int i = 0; i < 500; ++i) {
            CounterAndViewer c = new CounterAndViewer(s);
            threads[i] = new Thread(c);
        }
        runAll(threads);
        List<Integer> measurements = s.fetch();
        int sum = 0;
        for (Integer i : measurements) {
            sum += i.intValue();
        }
        assertTrue("Data lost.", 62375000 == sum);
    }


    private void runAll(Thread[] threads) {
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                // nop
            }
        }
    }
}
