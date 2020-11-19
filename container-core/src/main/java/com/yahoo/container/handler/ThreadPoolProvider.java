// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.container.handler.threadpool.DefaultContainerThreadpool;
import com.yahoo.container.protect.ProcessTerminator;
import com.yahoo.jdisc.Metric;

import java.util.concurrent.Executor;

/**
 * A configurable thread pool provider. This provides the worker threads used for normal request processing.
 * Request an Executor injected in your component constructor if you want to use it.
 *
 * @author Steinar Knutsen
 * @author baldersheim
 * @author bratseth
 */
public class ThreadPoolProvider extends AbstractComponent implements Provider<Executor> {

    private final ContainerThreadPool threadpool;

    @Inject
    public ThreadPoolProvider(ThreadpoolConfig config, Metric metric) {
        this.threadpool = new DefaultContainerThreadpool(translateConfig(config), metric);
    }

    public ThreadPoolProvider(ThreadpoolConfig config, Metric metric, ProcessTerminator processTerminator) {
        this.threadpool = new DefaultContainerThreadpool(translateConfig(config), metric, processTerminator);
    }

    /**
     * The underlying {@link ContainerThreadPool} uses a different config definition ({@link ContainerThreadpoolConfig})
     * as {@link ThreadpoolConfig} is currently public api.
     */
    private static ContainerThreadpoolConfig translateConfig(ThreadpoolConfig config) {
        return new ContainerThreadpoolConfig(
                new ContainerThreadpoolConfig.Builder()
                        .maxThreads(config.maxthreads())
                        .minThreads(config.corePoolSize())
                        .name(config.name())
                        .queueSize(config.queueSize())
                        .keepAliveTime(config.keepAliveTime())
                        .maxThreadExecutionTimeSeconds(config.maxThreadExecutionTimeSeconds()));
    }

    /**
     * Get the Executor provided by this class. This Executor will by default
     * also be used for search queries and processing requests.
     *
     * @return a possibly shared executor
     */
    @Override
    public Executor get() { return threadpool.executor(); }

    /**
     * Shut down the thread pool, give a grace period of 1 second before forcibly
     * shutting down all worker threads.
     */
    @Override
    public void deconstruct() {
        threadpool.close();
    }

}
