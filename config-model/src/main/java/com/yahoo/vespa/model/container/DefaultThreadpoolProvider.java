// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private final int metricsproxyNumThreads;

    DefaultThreadpoolProvider(ContainerCluster<?> cluster, int metricsproxyNumThreads) {
        super(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(
                        "default-threadpool",
                        ThreadPoolProvider.class.getName(),
                        null)));
        this.cluster = cluster;
        this.metricsproxyNumThreads = metricsproxyNumThreads;
    }

    private int defaultThreadsByClusterType() {
        if (cluster instanceof MetricsProxyContainerCluster) {
            return metricsproxyNumThreads;
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

        // Core pool size of 2xcores, and max of 100xcores and using a synchronous Q
        // This is the deafault pool used by both federation and generally when you ask for an Executor.
        builder.corePoolSize(-2).maxthreads(-100).queueSize(0);
    }
}
