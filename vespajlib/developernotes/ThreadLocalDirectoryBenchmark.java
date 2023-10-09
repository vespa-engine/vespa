// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;


import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark to compare ThreadLocalDirectory with java.util.concurrent's atomic
 * variables. Very low precision since it's an adapted unit test.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ThreadLocalDirectoryBenchmark {
    private static final int ITERATIONS = 500000;
    private final AtomicInteger atomicCounter = new AtomicInteger(0);
    private volatile int volatileCounter = 0;
    private int naiveCounter = 0;

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

    private static class Counter implements Runnable {
        ThreadLocalDirectory<Integer, Integer> r;

        Counter(ThreadLocalDirectory<Integer, Integer> r) {
            this.r = r;
        }

        @Override
        public void run() {
            LocalInstance<Integer, Integer> s = r.getLocalInstance();
            for (int i = 0; i < ITERATIONS; ++i) {
                r.update(Integer.valueOf(i), s);
            }
        }
    }

    private static class MutableSumUpdater implements ThreadLocalDirectory.Updater<IntWrapper, IntWrapper> {

        @Override
        public IntWrapper update(IntWrapper current, IntWrapper x) {
            current.counter += x.counter;
            return current;
        }

        @Override
        public IntWrapper createGenerationInstance(IntWrapper previous) {
            return new IntWrapper();
        }
    }

    private static class IntWrapper {
        public int counter = 0;
    }

    private static class WrapperCounter implements Runnable {
        ThreadLocalDirectory<IntWrapper, IntWrapper> r;

        WrapperCounter(ThreadLocalDirectory<IntWrapper, IntWrapper> r) {
            this.r = r;
        }

        @Override
        public void run() {
            LocalInstance<IntWrapper, IntWrapper> s = r.getLocalInstance();
            IntWrapper w = new IntWrapper();
            for (int i = 0; i < ITERATIONS; ++i) {
                w.counter = i;
                r.update(w, s);
            }
        }
    }

    private class AtomicCounter implements Runnable {
        @Override
        public void run() {
            for (int i = 0; i < ITERATIONS; ++i) {
                atomicCounter.addAndGet(i);
            }
        }
    }

    /**
     * This just bangs on a shared volatile to give an idea of the basic cost of
     * sharing a single variable with a memory barrier.
     */
    private class VolatileSillyness implements Runnable {

        @Override
        public void run() {
            for (int i = 0; i < ITERATIONS; ++i) {
                volatileCounter += i;
            }
        }
    }

    /**
     * This just bangs on a shared to give some sort of lower bound for time
     * elapsed.
     */
    private class SillySillyness implements Runnable {

        @Override
        public void run() {
            for (int i = 0; i < ITERATIONS; ++i) {
                naiveCounter += i;
            }
        }
    }

    private void sumFromMultipleThreads() {
        SumUpdater updater = new SumUpdater();
        ThreadLocalDirectory<Integer, Integer> s = new ThreadLocalDirectory<Integer, Integer>(updater);
        Thread[] threads = new Thread[500];
        for (int i = 0; i < 500; ++i) {
            Counter c = new Counter(s);
            threads[i] = new Thread(c);
        }
        runAll(threads);
        List<Integer> measurements = s.fetch();
        long sum = 0;
        for (Integer i : measurements) {
            sum += i.intValue();
        }
        System.out.println("Sum from all threads: " + sum);
    }

    private void sumMutableFromMultipleThreads() {
        MutableSumUpdater updater = new MutableSumUpdater();
        ThreadLocalDirectory<IntWrapper, IntWrapper> s = new ThreadLocalDirectory<IntWrapper, IntWrapper>(updater);
        Thread[] threads = new Thread[500];
        for (int i = 0; i < 500; ++i) {
            WrapperCounter c = new WrapperCounter(s);
            threads[i] = new Thread(c);
        }
        runAll(threads);
        List<IntWrapper> measurements = s.fetch();
        long sum = 0;
        for (IntWrapper i : measurements) {
            sum += i.counter;
        }
        System.out.println("Sum from all threads: " + sum);
    }

    private void sumAtomicFromMultipleThreads() {
        Thread[] threads = new Thread[500];
        for (int i = 0; i < 500; ++i) {
            AtomicCounter c = new AtomicCounter();
            threads[i] = new Thread(c);
        }
        runAll(threads);
        System.out.println("Sum from all threads: " + atomicCounter.get());
    }

    private void overwriteVolatileFromMultipleThreads() {
        Thread[] threads = new Thread[500];
        for (int i = 0; i < 500; ++i) {
            VolatileSillyness c = new VolatileSillyness();
            threads[i] = new Thread(c);
        }
        runAll(threads);
        System.out.println("Checksum from all threads: " + volatileCounter);
    }

    private void overwriteIntegerFromMultipleThreads() {
        Thread[] threads = new Thread[500];
        for (int i = 0; i < 500; ++i) {
            SillySillyness c = new SillySillyness();
            threads[i] = new Thread(c);
        }
        runAll(threads);
        System.out.println("Checksum from all threads: " + volatileCounter);
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

    public static void main(String[] args) {
        ThreadLocalDirectoryBenchmark benchmark = new ThreadLocalDirectoryBenchmark();
        long end;
        System.out.println("ThreadLocalDirectory<Integer, Integer>");
        long start = System.currentTimeMillis();
        benchmark.sumFromMultipleThreads();
        end = System.currentTimeMillis();
        System.out.println("Elapsed using threadlocals: " + (end - start) + " ms.");
        System.out.println("AtomicInteger");
        start = System.currentTimeMillis();
        benchmark.sumAtomicFromMultipleThreads();
        end = System.currentTimeMillis();
        System.out.println("Elapsed using atomic integer: " + (end - start) + " ms.");
        System.out.println("volatile int += volatile int");
        start = System.currentTimeMillis();
        benchmark.overwriteVolatileFromMultipleThreads();
        end = System.currentTimeMillis();
        System.out.println("Elapsed using single shared volatile: " + (end - start) + " ms.");
        System.out.println("int += int");
        start = System.currentTimeMillis();
        benchmark.overwriteIntegerFromMultipleThreads();
        end = System.currentTimeMillis();
        System.out.println("Checksum: " + benchmark.naiveCounter);
        System.out.println("Elapsed using shared int: " + (end - start) + " ms.");
        System.out.println("ThreadLocalDirectory<IntWrapper, IntWrapper>");
        start = System.currentTimeMillis();
        benchmark.sumMutableFromMultipleThreads();
        end = System.currentTimeMillis();
        System.out.println("Elapsed using threadlocal with mutable int wrapper: " + (end - start) + " ms.");
    }

}
