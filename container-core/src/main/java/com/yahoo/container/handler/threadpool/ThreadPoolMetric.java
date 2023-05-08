// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.threadpool;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.jdisc.Metric;

import java.util.Map;

/**
 * @author bjorncs
 */
class ThreadPoolMetric {

    private static final String THREAD_POOL_NAME_DIMENSION = "threadpool";

    private final Metric metric;
    private final Metric.Context defaultContext;
    private final String threadPoolName;

    ThreadPoolMetric(Metric metric, String threadPoolName) {
        this.metric = metric;
        this.threadPoolName = threadPoolName;
        this.defaultContext = metric.createContext(Map.of(THREAD_POOL_NAME_DIMENSION, threadPoolName));
    }

    void reportRejectRequest() {
        metric.add(ContainerMetrics.SERVER_REJECTED_REQUESTS.baseName(), 1L, defaultContext);
        metric.add(ContainerMetrics.JDISC_THREAD_POOL_REJECTED_TASKS.baseName(), 1L, defaultContext);
    }

    void reportThreadPoolSize(long size) {
        metric.set(ContainerMetrics.SERVER_THREAD_POOL_SIZE.baseName(), size, defaultContext);
        metric.set(ContainerMetrics.JDISC_THREAD_POOL_SIZE.baseName(), size, defaultContext);
    }

    void reportMaxAllowedThreadPoolSize(long size) {
        metric.set(ContainerMetrics.JDISC_THREAD_POOL_MAX_ALLOWED_SIZE.baseName(), size, defaultContext);
    }

    void reportActiveThreads(long threads) {
        metric.set(ContainerMetrics.SERVER_ACTIVE_THREADS.baseName(), threads, defaultContext);
        metric.set(ContainerMetrics.JDISC_THREAD_POOL_ACTIVE_THREADS.baseName(), threads, defaultContext);
    }

    void reportWorkQueueCapacity(long capacity) {
        metric.set(ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_CAPACITY.baseName(), capacity, defaultContext);
    }

    void reportWorkQueueSize(long size) {
        metric.set(ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_SIZE.baseName(), size, defaultContext);
    }

    void reportUnhandledException(Throwable t) {
        Metric.Context ctx = metric.createContext(Map.of(
                THREAD_POOL_NAME_DIMENSION, threadPoolName,
                "exception", t.getClass().getSimpleName()));
        metric.set(ContainerMetrics.JDISC_THREAD_POOL_UNHANDLED_EXCEPTIONS.baseName(), 1L, ctx);
    }

}
