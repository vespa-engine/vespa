// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.threadpool;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.protect.ProcessTerminator;
import com.yahoo.jdisc.Metric;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Default implementation of {@link ContainerThreadPool}.
 *
 * @author Steinar Knutsen
 * @author baldersheim
 * @author bratseth
 * @author bjorncs
 */
public class ContainerThreadpoolImpl extends AbstractComponent implements AutoCloseable, ContainerThreadPool {

    private static final Logger log = Logger.getLogger(ContainerThreadpoolImpl.class.getName());
    private static final int MIN_QUEUE_SIZE = 650;
    private static final int MIN_THREADS_WHEN_SCALE_FACTOR = 8;

    private final ExecutorServiceWrapper threadpool;

    @Inject
    public ContainerThreadpoolImpl(ContainerThreadpoolConfig config, Metric metric) {
        this(config, metric, new ProcessTerminator());
    }

    public ContainerThreadpoolImpl(ContainerThreadpoolConfig config, Metric metric, ProcessTerminator processTerminator) {
        this(config, metric, processTerminator, Runtime.getRuntime().availableProcessors());
    }

    ContainerThreadpoolImpl(ContainerThreadpoolConfig config, Metric metric, ProcessTerminator processTerminator,
                            int cpus) {
        String name = config.name();
        int maxThreads = maxThreads(config, cpus);
        int minThreads = minThreads(config, maxThreads, cpus);
        int queueSize = queueSize(config, maxThreads);
        log.info(String.format("Threadpool '%s': min=%d, max=%d, queue=%d", name, minThreads, maxThreads, queueSize));

        ThreadPoolMetric threadPoolMetric = new ThreadPoolMetric(metric, name);
        WorkerCompletionTimingThreadPoolExecutor executor =
                new WorkerCompletionTimingThreadPoolExecutor(minThreads, maxThreads,
                        (int)config.keepAliveTime() * 1000, TimeUnit.MILLISECONDS,
                        createQueue(queueSize),
                        ThreadFactoryFactory.getThreadFactory(name),
                        threadPoolMetric);
        // Prestart needed, if not all threads will be created by the fist N tasks and hence they might also
        // get the dreaded thread locals initialized even if they will never run.
        // That counters what we want to achieve with the Q that will prefer thread locality.
        executor.prestartAllCoreThreads();
        threadpool = new ExecutorServiceWrapper(
                executor, threadPoolMetric, processTerminator, config.maxThreadExecutionTimeSeconds() * 1000L,
                name);
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

    private static BlockingQueue<Runnable> createQueue(int size) {
        return size == 0 ? new SynchronousQueue<>(false) : new ArrayBlockingQueue<>(size);
    }

    private static int maxThreads(ContainerThreadpoolConfig config, int cpus) {
        if (config.maxThreads() > 0) return config.maxThreads();
        else if (config.maxThreads() == 0) return 4 * cpus;
        else return Math.max(MIN_THREADS_WHEN_SCALE_FACTOR, Math.abs(config.maxThreads()) * cpus);
    }

    private static int minThreads(ContainerThreadpoolConfig config, int max, int cpus) {
        int threads;
        if (config.minThreads() > 0) threads = config.minThreads();
        else if (config.minThreads() == 0) threads = 4 * cpus;
        else threads = Math.max(MIN_THREADS_WHEN_SCALE_FACTOR, Math.abs(config.minThreads()) * cpus);
        return Math.min(threads, max);
    }

    private int queueSize(ContainerThreadpoolConfig config, int maxThreads) {
        return config.queueSize() >= 0 ? config.queueSize() : Math.max(MIN_QUEUE_SIZE, Math.abs(config.queueSize()) * maxThreads);
    }

}
