// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.threadpool;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.protect.ProcessTerminator;
import com.yahoo.jdisc.Metric;
import com.yahoo.text.Text;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Default implementation of {@link ContainerThreadPool}.
 *
 * @author Steinar Knutsen
 * @author Henning Baldersheim
 * @author bratseth
 * @author bjorncs
 */
public class ContainerThreadpoolImpl extends AbstractComponent implements AutoCloseable, ContainerThreadPool {

    private static final Logger log = Logger.getLogger(ContainerThreadpoolImpl.class.getName());

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
        boolean hasAbsThreads = (config.minThreads() >= 0 && config.maxThreads() > 0);
        boolean hasRelThreads = (config.relativeMinThreads() >= 0 && config.relativeMaxThreads() > 0);

        if (!hasAbsThreads && !hasRelThreads) {
            throw new IllegalArgumentException("Requires either absolute or relative thread min/max to be configured. "
                    + summarizeConfigToString(config, cpus));
        }

        if (hasAbsThreads == hasRelThreads) {
            throw new IllegalArgumentException("Cannot have both absolute and relative min/max configured at the same time. "
                    + summarizeConfigToString(config, cpus));
        }

        if (config.minThreads() > config.maxThreads() || config.relativeMinThreads() > config.relativeMaxThreads()) {
            throw new IllegalArgumentException("Min cannot be greater than max. "
                    + summarizeConfigToString(config, cpus));
        }

        boolean hasAbsQueueSize = (config.queueSize() >= 0);
        boolean hasRelQueueSize = (config.relativeQueueSize() >= 0);

        if (!hasAbsQueueSize && !hasRelQueueSize) {
            throw new IllegalArgumentException("Requires either absolute or relative queueSize to be configured. "
                    + summarizeConfigToString(config, cpus));
        }

        if (hasAbsQueueSize == hasRelQueueSize) {
            throw new IllegalArgumentException("Cannot have both absolute and relative configured at the same time. "
                    + summarizeConfigToString(config, cpus));
        }

        String name = config.name();
        int maxThreads = maxThreads(config, cpus, hasRelThreads);
        int minThreads = minThreads(config, cpus, hasRelThreads);
        int queueSize = queueSize(config, maxThreads, hasRelQueueSize);

        log.config(Text.format("Threadpool '%s': min=%d, max=%d, queue=%s", name, minThreads, maxThreads, queueSizeToString(queueSize)));

        ThreadPoolMetric threadPoolMetric = new ThreadPoolMetric(metric, name);
        WorkerCompletionTimingThreadPoolExecutor executor =
                new WorkerCompletionTimingThreadPoolExecutor(minThreads, maxThreads,
                        (long) config.keepAliveTime() * 1000, TimeUnit.MILLISECONDS,
                        createQueue(queueSize),
                        ThreadFactoryFactory.getThreadFactory(name),
                        threadPoolMetric);
        // Pre-start needed, if not all threads will be created by the fist N tasks and hence they might also
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

    /** For the components requiring infinite queue, they must specify Integer.MAX_VALUE */
    private static BlockingQueue<Runnable> createQueue(int size) {
        if (size == Integer.MAX_VALUE) {
            return new LinkedBlockingQueue<>();
        }

        return size == 0 ? new SynchronousQueue<>(false) : new ArrayBlockingQueue<>(size);
    }

    private static int maxThreads(ContainerThreadpoolConfig config, int cpus, boolean relative) {
        if (relative) {
            return (int) Math.round(config.relativeMaxThreads() * cpus);
        } else {
            return config.maxThreads();
        }
    }

    private static int minThreads(ContainerThreadpoolConfig config, int cpus, boolean relative) {
        if (relative) {
            return (int) Math.round(config.relativeMinThreads() * cpus);
        } else {
            return config.minThreads();
        }
    }

    private int queueSize(ContainerThreadpoolConfig config, int maxThreads, boolean relative) {
        if (relative) {
            return (int) Math.round(config.relativeQueueSize() * maxThreads);
        } else {
            return config.queueSize();
        }
    }

    /** Summary string of the config for exceptions. */
    private static String summarizeConfigToString(ContainerThreadpoolConfig c, int cpus) {
        return Text.format(
                "abs[min=%d,max=%d,queue=%d] rel[min=%.3f,max=%.3f,queue=%.3f] cpus=%d",
                c.minThreads(), c.maxThreads(), c.queueSize(),
                c.relativeMinThreads(), c.relativeMaxThreads(), c.relativeQueueSize(),
                cpus
        );
    }

    private String queueSizeToString(int queueSize) {
        if (queueSize == Integer.MAX_VALUE) {
            return "unlimited";
        }
        return Integer.toString(queueSize);
    }

}
