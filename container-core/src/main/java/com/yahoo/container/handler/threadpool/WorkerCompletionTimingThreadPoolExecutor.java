// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.threadpool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread pool executor which maintains the last time a worker completed
 * package private for testing
 *
 * @author Steinar Knutsen
 * @author baldersheim
 * @author bratseth
 */
class WorkerCompletionTimingThreadPoolExecutor extends ThreadPoolExecutor {

    volatile long lastThreadAssignmentTimeMillis = System.currentTimeMillis();
    private final AtomicLong startedCount = new AtomicLong(0);
    private final AtomicLong completedCount = new AtomicLong(0);
    private final ThreadPoolMetric metric;

    WorkerCompletionTimingThreadPoolExecutor(int corePoolSize,
                                             int maximumPoolSize,
                                             long keepAliveTime,
                                             TimeUnit unit,
                                             BlockingQueue<Runnable> workQueue,
                                             ThreadFactory threadFactory,
                                             ThreadPoolMetric metric) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        this.metric = metric;
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        lastThreadAssignmentTimeMillis = System.currentTimeMillis();
        startedCount.incrementAndGet();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        completedCount.incrementAndGet();
        if (t != null) {
            metric.reportUnhandledException(t);
        }
    }

    @Override
    public int getActiveCount() {
        return (int)(startedCount.get() - completedCount.get());
    }

}

