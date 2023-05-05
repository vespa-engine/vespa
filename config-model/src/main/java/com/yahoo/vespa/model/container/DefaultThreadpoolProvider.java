// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

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
    private final int defaultWorkerThreads;

    DefaultThreadpoolProvider(ContainerCluster<?> cluster, int defaultWorkerThreads) {
        super(new ComponentModel(
                BundleInstantiationSpecification.fromStrings(
                        "default-threadpool",
                        ThreadPoolProvider.class.getName(),
                        null)));
        this.cluster = cluster;
        this.defaultWorkerThreads = defaultWorkerThreads;
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        if (cluster instanceof ApplicationContainerCluster) {
            // Core pool size of 2xcores, and max of 100xcores and using a synchronous Q
            // This is the default pool used by both federation and generally when you ask for an Executor.
            builder.corePoolSize(-2).maxthreads(-100).queueSize(0);
        } else {
            // Container clusters such as logserver, metricsproxy and clustercontroller
            builder.corePoolSize(defaultWorkerThreads).maxthreads(defaultWorkerThreads).queueSize(50);
        }
    }
}
