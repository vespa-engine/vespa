// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.ThreadPoolProvider;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

/**
 * Component definition for the jdisc default threadpool provider ({@link ThreadPoolProvider}).
 *
 * @author bjorncs
 */
class DefaultThreadpoolProvider extends SimpleComponent implements ThreadpoolConfig.Producer {

    private final ContainerCluster<?> cluster;
    private final DeployState deployState;

    DefaultThreadpoolProvider(ContainerCluster<?> cluster, DeployState deployState) {
        super(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(
                        "default-threadpool",
                        ThreadPoolProvider.class.getName(),
                        null)));
        this.cluster = cluster;
        this.deployState = deployState;
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        if (!(cluster instanceof ApplicationContainerCluster)) {
            // Container clusters such as logserver, metricsproxy and clustercontroller
            int defaultWorkerThreads = 10;
            builder.maxthreads(defaultWorkerThreads);
            builder.corePoolSize(defaultWorkerThreads);
            builder.queueSize(50);
            return;
        }

        double threadPoolSizeFactor = deployState.getProperties().threadPoolSizeFactor();
        double vcpu = ContainerThreadpool.vcpu(cluster).orElse(0);
        if (threadPoolSizeFactor <= 0 || vcpu == 0) return;

        // Configuration is currently identical to the search handler's threadpool
        int workerThreads = Math.max(8, (int)Math.ceil(vcpu * threadPoolSizeFactor));
        builder.maxthreads(workerThreads);
        builder.corePoolSize(workerThreads);
        builder.queueSize((int)(workerThreads * deployState.getProperties().queueSizeFactor()));
    }
}
