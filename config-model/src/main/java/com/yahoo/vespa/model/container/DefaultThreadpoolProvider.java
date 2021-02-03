// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.ThreadPoolProvider;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster;
import com.yahoo.vespa.model.container.component.SimpleComponent;

/**
 * Component definition for the jdisc default threadpool provider ({@link ThreadPoolProvider}).
 *
 * @author bjorncs
 */
class DefaultThreadpoolProvider extends SimpleComponent implements ThreadpoolConfig.Producer {

    private final ContainerCluster<?> cluster;

    DefaultThreadpoolProvider(ContainerCluster<?> cluster) {
        super(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(
                        "default-threadpool",
                        ThreadPoolProvider.class.getName(),
                        null)));
        this.cluster = cluster;
    }

    private int defaultThreadsByClusterType() {
        if (cluster instanceof MetricsProxyContainerCluster) {
            return 4;
        }
        return 10;
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        if (!(cluster instanceof ApplicationContainerCluster)) {
            // Container clusters such as logserver, metricsproxy and clustercontroller
            int defaultWorkerThreads = defaultThreadsByClusterType();
            builder.maxthreads(defaultWorkerThreads);
            builder.corePoolSize(defaultWorkerThreads);
            builder.queueSize(50);
            return;
        }

        double vcpu = cluster.vcpu().orElse(0);
        if (vcpu == 0) return;

        // Configuration is currently identical to the search handler's threadpool
        int workerThreads = Math.max(8, (int)Math.ceil(vcpu * 2.0));
        builder.maxthreads(workerThreads);
        builder.corePoolSize(workerThreads);
        builder.queueSize((int)(workerThreads * 40.0));
    }
}
