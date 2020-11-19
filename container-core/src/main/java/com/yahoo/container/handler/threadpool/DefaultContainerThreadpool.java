// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.threadpool;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.protect.ProcessTerminator;
import com.yahoo.jdisc.Metric;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of {@link DefaultContainerThreadpool}.
 *
 * @author Steinar Knutsen
 * @author baldersheim
 * @author bratseth
 * @author bjorncs
 */
public class DefaultContainerThreadpool extends AbstractComponent implements AutoCloseable, ContainerThreadPool {

    private final ExecutorServiceWrapper threadpool;

    @Inject
    public DefaultContainerThreadpool(ContainerThreadpoolConfig config, Metric metric) {
        this(config, metric, new ProcessTerminator());
    }

    public DefaultContainerThreadpool(ContainerThreadpoolConfig config, Metric metric, ProcessTerminator processTerminator) {
        ThreadPoolMetric threadPoolMetric = new ThreadPoolMetric(metric, config.name());
        int maxNumThreads = computeMaximumThreadPoolSize(config.maxThreads());
        int coreNumThreads = computeCoreThreadPoolSize(config.minThreads(), maxNumThreads);
        WorkerCompletionTimingThreadPoolExecutor executor =
                new WorkerCompletionTimingThreadPoolExecutor(coreNumThreads, maxNumThreads,
                        (int)config.keepAliveTime() * 1000, TimeUnit.MILLISECONDS,
                        createQ(config.queueSize(), maxNumThreads),
                        ThreadFactoryFactory.getThreadFactory(config.name()),
                        threadPoolMetric);
        // Prestart needed, if not all threads will be created by the fist N tasks and hence they might also
        // get the dreaded thread locals initialized even if they will never run.
        // That counters what we we want to achieve with the Q that will prefer thread locality.
        executor.prestartAllCoreThreads();
        threadpool = new ExecutorServiceWrapper(
                executor, threadPoolMetric, processTerminator, config.maxThreadExecutionTimeSeconds() * 1000L,
                config.name(), config.queueSize());
    }

    @Override public Executor executor() { return threadpool; }

    @Override public void close() { closeInternal(); }

    @Override public void deconstruct() { closeInternal(); super.deconstruct(); }

    /**
     * Shutdown the thread pool, give a grace period of 1 second before forcibly
     * shutting down all worker threads.
     */
    private void closeInternal() {
        boolean terminated;

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

    private static BlockingQueue<Runnable> createQ(int queueSize, int maxThreads) {
        return (queueSize == 0)
                ? new SynchronousQueue<>(false)
                : (queueSize < 0)
                ? new ArrayBlockingQueue<>(maxThreads*4)
                : new ArrayBlockingQueue<>(queueSize);
    }

    private static int computeMaximumThreadPoolSize(int maxNumThreads) {
        return (maxNumThreads <= 0)
                ? Runtime.getRuntime().availableProcessors() * 4
                : maxNumThreads;
    }

    private static int computeCoreThreadPoolSize(int corePoolSize, int maxNumThreads) {
        return Math.min(
                corePoolSize <= 0 ? Runtime.getRuntime().availableProcessors() * 2 : corePoolSize,
                maxNumThreads);
    }

}
