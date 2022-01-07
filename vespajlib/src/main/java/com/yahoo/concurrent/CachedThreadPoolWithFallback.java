// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * An executor that will first try a bounded cached thread pool before falling back to an unbounded
 * single threaded thread pool that will take over dispatching to the primary pool.
 */
public class CachedThreadPoolWithFallback implements AutoCloseable, Executor {

    private final ExecutorService primary;
    private final ExecutorService secondary;

    public CachedThreadPoolWithFallback(String baseName, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit timeUnit) {
        primary = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, timeUnit,
                new SynchronousQueue<>(), ThreadFactoryFactory.getDaemonThreadFactory(baseName + ".primary"));
        secondary = Executors.newSingleThreadExecutor(ThreadFactoryFactory.getDaemonThreadFactory(baseName + ".secondary"));
    }

    @Override
    public void execute(Runnable command) {
        try {
            primary.execute(command);
        } catch (RejectedExecutionException e1) {
            secondary.execute(() -> retryForever(command));
        }
    }

    private void retryForever(Runnable command) {
        while (true) {
            try {
                primary.execute(command);
                return;
            } catch (RejectedExecutionException rejected) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException silenced) { }
            }
        }
    }

    @Override
    public void close() {
        secondary.shutdown();
        join(secondary);
        primary.shutdown();
        join(primary);
    }

    private static void join(ExecutorService executor) {
        while (true) {
            try {
                if (executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {}
        }
    }

}
