// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.provision.Flavor;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.search.config.QrStartConfig;

/**
 * Tuning of qr-start config for a container service based on the node flavor of that node.
 *
 * @author balder
 */
public class NodeFlavorTuning implements
        QrStartConfig.Producer,
        ThreadpoolConfig.Producer
{

    private final Flavor flavor;

    public NodeFlavorTuning setThreadPoolSizeFactor(double threadPoolSizeFactor) {
        this.threadPoolSizeFactor = threadPoolSizeFactor;
        return this;
    }

    public NodeFlavorTuning setQueueSizeFactor(double queueSizeFactor) {
        this.queueSizeFactor = queueSizeFactor;
        return this;
    }

    private double threadPoolSizeFactor = 8.0;
    private double queueSizeFactor = 8.0;

    NodeFlavorTuning(Flavor flavor) {
        this.flavor = flavor;
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        builder.jvm.availableProcessors(Math.max(2, (int)Math.ceil(flavor.getMinCpuCores())));
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        // Controls max number of concurrent requests per container
        int workerThreads = Math.max(2, (int)Math.ceil(flavor.getMinCpuCores() * threadPoolSizeFactor));
        builder.maxthreads(workerThreads);

        // This controls your burst handling capability.
        // 0 => No extra burst handling beyond you max concurrent requests (maxthreads).
        // N => N times max concurrent requests as a buffer for handling bursts
        builder.queueSize((int)(workerThreads * queueSizeFactor));
    }
}
