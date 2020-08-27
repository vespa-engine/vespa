// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.time.Duration;

/**
 * Component definition for a {@link java.util.concurrent.Executor} using {@link ContainerThreadPool}.
 *
 * @author bjorncs
 */
public class ThreadPoolExecutorComponent extends SimpleComponent implements ThreadpoolConfig.Producer {

    private final String name;
    private final Integer maxPoolSize;
    private final Integer corePoolSize;
    private final Duration keepAliveTime;
    private final Integer queueSize;
    private final Duration maxThreadExecutionTime;

    private ThreadPoolExecutorComponent(Builder builder) {
        super(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(
                        "threadpool@" + builder.name,
                        ContainerThreadPool.class.getName(),
                        null)));
        this.name = builder.name;
        this.maxPoolSize = builder.maxPoolSize;
        this.corePoolSize = builder.corePoolSize;
        this.keepAliveTime = builder.keepAliveTime;
        this.queueSize = builder.queueSize;
        this.maxThreadExecutionTime = builder.maxThreadExecutionTime;
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        builder.name(this.name);
        if (maxPoolSize != null) builder.maxthreads(maxPoolSize);
        if (corePoolSize != null) builder.corePoolSize(corePoolSize);
        if (keepAliveTime != null) builder.keepAliveTime(keepAliveTime.toMillis() / 1000D);
        if (queueSize != null) builder.queueSize(queueSize);
        if (maxThreadExecutionTime != null) builder.maxThreadExecutionTimeSeconds((int)maxThreadExecutionTime.toMillis() / 1000);
    }

    public static class Builder {

        private final String name;
        private Integer maxPoolSize;
        private Integer corePoolSize;
        private Duration keepAliveTime;
        private Integer queueSize;
        private Duration maxThreadExecutionTime;

        public Builder(String name) { this.name = name; }

        public Builder maxPoolSize(int size) { this.maxPoolSize = size; return this; }
        public Builder corePoolSize(int size) { this.corePoolSize = size; return this; }
        public Builder keepAliveTime(Duration time) { this.keepAliveTime = time; return this; }
        public Builder queueSize(int size) { this.queueSize = size; return this; }
        public Builder maxThreadExecutionTime(Duration time) { this.maxThreadExecutionTime = time; return this; }

        public ThreadPoolExecutorComponent build() { return new ThreadPoolExecutorComponent(this); }

    }
}
