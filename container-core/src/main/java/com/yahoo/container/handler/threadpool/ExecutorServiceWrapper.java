// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.threadpool;

import com.google.common.util.concurrent.ForwardingExecutorService;
import com.yahoo.container.protect.ProcessTerminator;
import com.yahoo.jdisc.Metric;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A service executor wrapper which emits metrics and
 * shuts down the vm when no workers are available for too long to avoid containers lingering in a blocked state.
 * Package private for testing
 *
 * @author Steinar Knutsen
 * @author baldersheim
 * @author bratseth
 */
class ExecutorServiceWrapper extends ForwardingExecutorService {

    private final WorkerCompletionTimingThreadPoolExecutor wrapped;
    private final Metric metric;
    private final ProcessTerminator processTerminator;
    private final long maxThreadExecutionTimeMillis;
    private final Thread metricReporter;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    ExecutorServiceWrapper(
            WorkerCompletionTimingThreadPoolExecutor wrapped,
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
     * Tracks all instances of {@link RejectedExecutionException}.
     * {@link ContainerThreadPool} returns an executor, so external uses will not
     * have access to the methods declared by {@link ExecutorService}.
     * ({@link Executor#execute(Runnable)} is declared by {@link Executor}.)
     */
    @Override
    public void execute(Runnable command) {
        try {
            super.execute(command);
        } catch (RejectedExecutionException e) {
            metric.add(MetricNames.REJECTED_REQUEST, 1, null);
            long timeSinceLastReturnedThreadMillis = System.currentTimeMillis() - wrapped.lastThreadAssignmentTimeMillis;
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

