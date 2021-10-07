// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.threadpool;

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

    void reportRejectRequest() { metric.add("serverRejectedRequests", 1L, defaultContext); }
    void reportThreadPoolSize(long size) { metric.set("serverThreadPoolSize", size, defaultContext); }
    void reportActiveThreads(long threads) { metric.set("serverActiveThreads", threads, defaultContext); }
    void reportWorkQueueCapacity(long capacity) { metric.set("jdisc.thread_pool.work_queue.capacity", capacity, defaultContext); }
    void reportWorkQueueSize(long size) { metric.set("jdisc.thread_pool.work_queue.size", size, defaultContext); }
    void reportUnhandledException(Throwable t) {
        Metric.Context ctx = metric.createContext(Map.of(
                THREAD_POOL_NAME_DIMENSION, threadPoolName,
                "exception", t.getClass().getSimpleName()));
        metric.set("jdisc.thread_pool.unhandled_exceptions", 1L, ctx);
    }

}
