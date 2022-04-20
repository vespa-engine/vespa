// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.threadpool;

import com.google.common.util.concurrent.ForwardingExecutorService;
import com.yahoo.container.protect.ProcessTerminator;

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
    private final ThreadPoolMetric metric;
    private final ProcessTerminator processTerminator;
    private final long maxThreadExecutionTimeMillis;
    private final int queueCapacity;
    private final Thread metricReporter;
    private final boolean threadPoolIsOnlyQ;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    ExecutorServiceWrapper(WorkerCompletionTimingThreadPoolExecutor wrapped,
                           ThreadPoolMetric metric,
                           ProcessTerminator processTerminator,
                           long maxThreadExecutionTimeMillis,
                           String name) {
        this.wrapped = wrapped;
        this.metric = metric;
        this.processTerminator = processTerminator;
        this.maxThreadExecutionTimeMillis = maxThreadExecutionTimeMillis;
        int maxQueueCapacity = wrapped.getQueue().remainingCapacity() + wrapped.getQueue().size();
        this.threadPoolIsOnlyQ = (maxQueueCapacity == 0);
        this.queueCapacity = threadPoolIsOnlyQ
                ? wrapped.getMaximumPoolSize()
                : maxQueueCapacity;
        reportMetrics();
        metricReporter = new Thread(this::reportMetricsRegularly);
        metricReporter.setName(name + "-threadpool-metric-reporter");
        metricReporter.start();
    }

    private void reportMetrics() {
        int activeThreads = wrapped.getActiveCount();
        metric.reportThreadPoolSize(wrapped.getPoolSize());
        metric.reportMaxAllowedThreadPoolSize(wrapped.getMaximumPoolSize());
        metric.reportActiveThreads(activeThreads);
        int queueSize = threadPoolIsOnlyQ ? activeThreads : wrapped.getQueue().size();
        metric.reportWorkQueueSize(queueSize);
        metric.reportWorkQueueCapacity(queueCapacity);
    }

    private void reportMetricsRegularly() {
        while (timeToReportMetricsAgain(100)) {
            reportMetrics();
        }
    }
    private boolean timeToReportMetricsAgain(int timeoutMS) {
        synchronized (closed) {
            if (!closed.get()) {
                try {
                    closed.wait(timeoutMS);
                } catch (InterruptedException e) {
                    return false;
                }
            }
        }
        return !closed.get();
    }

    @Override
    public void shutdown() {
        synchronized (closed) {
            closed.set(true);
            closed.notify();
        }
        try {
            metricReporter.join();
        } catch (InterruptedException e) {}
        super.shutdown();
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
            metric.reportRejectRequest();
            long timeSinceLastReturnedThreadMillis = System.currentTimeMillis() - wrapped.lastThreadAssignmentTimeMillis;
            if (timeSinceLastReturnedThreadMillis > maxThreadExecutionTimeMillis)
                processTerminator.logAndDie("No worker threads have been available for " +
                        timeSinceLastReturnedThreadMillis + " ms. Shutting down.", true);
            throw e;
        }
    }

    @Override
    protected ExecutorService delegate() { return wrapped; }

}

