// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.search.config.QrStartConfig;

/**
 * Tuning of qr-start config for a container service based on node resources.
 *
 * @author balder
 */
public class NodeResourcesTuning implements QrStartConfig.Producer, ThreadpoolConfig.Producer {

    private final NodeResources resources;

    public NodeResourcesTuning setThreadPoolSizeFactor(double threadPoolSizeFactor) {
        this.threadPoolSizeFactor = threadPoolSizeFactor;
        return this;
    }

    public NodeResourcesTuning setQueueSizeFactor(double queueSizeFactor) {
        this.queueSizeFactor = queueSizeFactor;
        return this;
    }

    private double threadPoolSizeFactor = 8.0;
    private double queueSizeFactor = 8.0;

    NodeResourcesTuning(NodeResources resources) {
        this.resources = resources;
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        builder.jvm.availableProcessors(Math.max(2, (int)Math.ceil(resources.vcpu())));
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        // Controls max number of concurrent requests per container
        int workerThreads = Math.max(2, (int)Math.ceil(resources.vcpu() * threadPoolSizeFactor));
        builder.maxthreads(workerThreads);

        // This controls your burst handling capability.
        // 0 => No extra burst handling beyond you max concurrent requests (maxthreads).
        // N => N times max concurrent requests as a buffer for handling bursts
        builder.queueSize((int)(workerThreads * queueSizeFactor));
    }
}
