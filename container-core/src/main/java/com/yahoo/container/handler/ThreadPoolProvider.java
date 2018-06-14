// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.google.common.util.concurrent.ForwardingExecutorService;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.protect.ProcessTerminator;
import com.yahoo.jdisc.Metric;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A configurable thread pool provider. This provides the worker threads used for normal request processing.
 * Request an Executor injected in your component constructor if you want to use it.
 *
 * @author Steinar Knutsen
 * @author baldersheim
 * @author bratseth
 */
public class ThreadPoolProvider extends AbstractComponent implements Provider<Executor> {

    private final ExecutorServiceWrapper threadpool;

    @Inject
    public ThreadPoolProvider(ThreadpoolConfig threadpoolConfig, Metric metric) {
        this(threadpoolConfig, metric, new ProcessTerminator());
    }

    public ThreadPoolProvider(ThreadpoolConfig threadpoolConfig, Metric metric, ProcessTerminator processTerminator) {
        WorkerCompletionTimingThreadPoolExecutor executor =
                new WorkerCompletionTimingThreadPoolExecutor(threadpoolConfig.maxthreads(),
                                                             threadpoolConfig.maxthreads(),
                                                             0L, TimeUnit.SECONDS,
                                                             new SynchronousQueue<>(false),
                                                             ThreadFactoryFactory.getThreadFactory("threadpool"));
        // Prestart needed, if not all threads will be created by the fist N tasks and hence they might also
        // get the dreaded thread locals initialized even if they will never run.
        // That counters what we we want to achieve with the Q that will prefer thread locality.
        executor.prestartAllCoreThreads();
        threadpool = new ExecutorServiceWrapper(executor, metric, processTerminator,
                                                threadpoolConfig.maxThreadExecutionTimeSeconds() * 1000L);
    }

    /**
     * Get the Executor provided by this class. This Executor will by default
     * also be used for search queries and processing requests.
     *
     * @return a possibly shared executor
     */
    @Override
    public Executor get() { return threadpool; }

    /**
     * Shutdown the thread pool, give a grace period of 1 second before forcibly
     * shutting down all worker threads.
     */
    @Override
    public void deconstruct() {
        boolean terminated;

        super.deconstruct();
        threadpool.shutdown();
        try {
            terminated = threadpool.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!terminated) {
            threadpool.shutdownNow();
        }
    }

    /**
     * A service executor wrapper which emits metrics and
     * shuts down the vm when no workers are available for too long to avoid containers lingering in a blocked state.
     */
    private final static class ExecutorServiceWrapper extends ForwardingExecutorService {

        private final WorkerCompletionTimingThreadPoolExecutor wrapped;
        private final Metric metric;
        private final ProcessTerminator processTerminator;
        private final long maxThreadExecutionTimeMillis;
        private final Thread metricReporter;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ExecutorServiceWrapper(WorkerCompletionTimingThreadPoolExecutor wrapped,
                                       Metric metric, ProcessTerminator processTerminator,
                                       long maxThreadExecutionTimeMillis) {
            this.wrapped = wrapped;
            this.metric = metric;
            this.processTerminator = processTerminator;
            this.maxThreadExecutionTimeMillis = maxThreadExecutionTimeMillis;

            metric.set(MetricNames.THREAD_POOL_SIZE, wrapped.getPoolSize(), null);
            metric.set(MetricNames.ACTIVE_THREADS, wrapped.getActiveCount(), null);
            metric.add(MetricNames.REJECTED_REQUEST, 0, null);
            metricReporter = new Thread(this::reportMetrics);
            metricReporter.setDaemon(true);
            metricReporter.start();
        }

        private final void reportMetrics() {
            try {
                while (!closed.get()) {
                    metric.set(MetricNames.THREAD_POOL_SIZE, wrapped.getPoolSize(), null);
                    metric.set(MetricNames.ACTIVE_THREADS, wrapped.getActiveCount(), null);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) { }
        }

        @Override
        public void shutdown() {
            super.shutdown();
            closed.set(true);
        }

        /**
         * Tracks all instances of RejectedExecutionException.
         * ThreadPoolProvider returns an executor, so external uses will not
         * have access to the methods declared by ExecutorService.
         * (execute(Runnable) is declared by Executor.)
         */
        @Override
        public void execute(Runnable command) {
            try {
                super.execute(command);
            } catch (RejectedExecutionException e) {
                metric.add(MetricNames.REJECTED_REQUEST, 1, null);
                long timeSinceLastReturnedThreadMillis = System.currentTimeMillis() - wrapped.lastThreadReturnTimeMillis;
                if (timeSinceLastReturnedThreadMillis > maxThreadExecutionTimeMillis)
                    processTerminator.logAndDie("No worker threads have been available for " +
                                                timeSinceLastReturnedThreadMillis + " ms. Shutting down.", true);
                throw e;
            }
        }

        @Override
        protected ExecutorService delegate() { return wrapped; }

        private static final class MetricNames {
            private static final String REJECTED_REQUEST = "serverRejectedRequests";
            private static final String THREAD_POOL_SIZE = "serverThreadPoolSize";
            private static final String ACTIVE_THREADS   = "serverActiveThreads";
        }

    }

    /** A thread pool executor which maintains the last time a worker completed */
    private final static class WorkerCompletionTimingThreadPoolExecutor extends ThreadPoolExecutor {

        volatile long lastThreadReturnTimeMillis = System.currentTimeMillis();
        private final AtomicLong startedCount = new AtomicLong(0);
        private final AtomicLong completedCount = new AtomicLong(0);

        public WorkerCompletionTimingThreadPoolExecutor(int corePoolSize,
                                                        int maximumPoolSize,
                                                        long keepAliveTime,
                                                        TimeUnit unit,
                                                        BlockingQueue<Runnable> workQueue,
                                                        ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            super.beforeExecute(t, r);
            lastThreadReturnTimeMillis = System.currentTimeMillis();
            startedCount.incrementAndGet();
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            completedCount.incrementAndGet();
        }

        @Override
        public int getActiveCount() {
            return (int)(startedCount.get() - completedCount.get());
        }
    }

}
