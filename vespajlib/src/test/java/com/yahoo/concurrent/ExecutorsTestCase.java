// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;

import java.util.LinkedList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorsTestCase {
    static private class Runner implements Runnable {
        static private AtomicInteger threadCount = new AtomicInteger(0);
        static private class ThreadId extends ThreadLocal<Integer> {
            @Override
            protected Integer initialValue() {
                return threadCount.getAndIncrement();
            }
        }
        static private ThreadId threadId = new ThreadId();
        private volatile int runBy = -1;
        @Override
        public void run() {
            runBy = threadId.get();
        }
        int getRunBy() { return runBy; }
    }

    private static class Producer implements Runnable {
        private volatile int maxThreadId = 0;
        private final long timeOutMS;
        private final ExecutorService consumer;
        Producer(ExecutorService consumer, long timeOutMS) {
            this.timeOutMS = timeOutMS;
            this.consumer = consumer;
        }
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            Runner r = new Runner();
            try {
                while (now + timeOutMS > System.currentTimeMillis()) {
                    Future<?> f = consumer.submit(r);
                    f.get();
                    maxThreadId = Math.max(maxThreadId, r.getRunBy());
                    Thread.sleep(1);

                }
            } catch (InterruptedException e) {
                assertTrue(false);
            } catch (ExecutionException e) {
                assertTrue(false);
            }

        }
    }

    private void assertThreadId(ExecutorService s, int id) throws InterruptedException, ExecutionException {
        Runner r = new Runner();
        Future<?> f = s.submit(r);
        assertNull(f.get());
        assertEquals(id, r.getRunBy());
    }
    private void assertRoundRobinOrder(ExecutorService s) throws InterruptedException, ExecutionException {
        assertThreadId(s, 0);
        assertThreadId(s, 1);
        assertThreadId(s, 2);
        assertThreadId(s, 0);
        assertThreadId(s, 1);
        assertThreadId(s, 2);
        assertThreadId(s, 0);
        assertThreadId(s, 1);
    }
    private int measureMaxNumThreadsUsage(ThreadPoolExecutor s, long durationMS, int maxProducers) throws InterruptedException, ExecutionException {
        s.prestartAllCoreThreads();
        ExecutorService consumers = Executors.newCachedThreadPool();
        LinkedList<Future<Producer>> futures = new LinkedList<>();
        for (int i = 0; i < maxProducers; i++) {
            Producer p = new Producer(s, durationMS);
            futures.add(consumers.submit(p, p));
        }
        int maxThreadId = 0;
        try {
            while (! futures.isEmpty()) {
                Producer p = futures.remove().get();
                maxThreadId = Math.max(maxThreadId, p.maxThreadId);
            }
        } catch (InterruptedException e) {
            assertTrue(false);
        } catch (ExecutionException e) {
            assertTrue(false);
        }
        return maxThreadId;
    }
    private void assertStackOrder(ThreadPoolExecutor s) throws InterruptedException, ExecutionException {
        s.prestartAllCoreThreads();
        Thread.sleep(10);           //Sleep to allow last executing thread to get back on the stack
        assertThreadId(s, 0);
        Thread.sleep(10);
        assertThreadId(s, 0);
        Thread.sleep(10);
        assertThreadId(s, 0);
        Thread.sleep(10);
        assertThreadId(s, 0);
        Thread.sleep(10);
        assertThreadId(s, 0);
        Thread.sleep(10);
        assertThreadId(s, 0);
        Thread.sleep(10);
        assertThreadId(s, 0);
    }
    
    @Ignore // Ignored as it is not deterministic, and probably hard to make deterministic to.
    @Test
    public void requireThatExecutionOrderIsPredictable() throws InterruptedException, ExecutionException {
        Runner.threadCount.set(0);
        assertRoundRobinOrder(new ThreadPoolExecutor(3, 3, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()));
        Runner.threadCount.set(0);
        assertRoundRobinOrder(new ThreadPoolExecutor(3, 3, 0L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(true)));
        Runner.threadCount.set(0);
        assertStackOrder(new ThreadPoolExecutor(3, 3, 0L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(false)));
    }
    
    @Ignore // Ignored as it might not be deterministic
    public void requireThatExecutionOrderIsPredictableUnderLoad() throws InterruptedException, ExecutionException {
        Runner.threadCount.set(0);
        assertEquals(99, measureMaxNumThreadsUsage(new ThreadPoolExecutor(100, 100, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()), 3000, 10));
        Runner.threadCount.set(0);
        assertEquals(99, measureMaxNumThreadsUsage(new ThreadPoolExecutor(100, 100, 0L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(true)), 3000, 10));
        Runner.threadCount.set(0);
        //Max 9 concurrent tasks. Might not be deterministic
        assertEquals(9, measureMaxNumThreadsUsage(new ThreadPoolExecutor(100, 100, 0L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(false)), 3000, 10));
        Runner.threadCount.set(0);
    }
}
